import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from AI.Domain.Algorithms.traffic_anomaly import DROP, SURGE, TrafficAnomalyDetector
from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder
from AI.Domain.Datasets.traffic_reading import TrafficReading
from AI.Orchestration.kafka_config_loader import KafkaConfigLoader
from AI.Orchestration.kafka_consumer import KafkaConsumerConfig, TrafficReadingKafkaConsumer
from AI.Orchestration.kafka_prediction_producer import PredictionPublishError
from AI.Orchestration.request_manager import Request, RequestManager
from AI.Orchestration.traffic_pipeline import TrafficPredictionPipeline

T0 = datetime(2017, 3, 6, 6, tzinfo=timezone.utc)
HOUR = timedelta(hours=1)


def reading_event(i=0, junction="1", vehicles=20):
    return {
        "eventId": f"traffic-{junction}-{i}",
        "source": "test",
        "junctionId": junction,
        "readingTimestamp": (T0 + i * HOUR).isoformat().replace("+00:00", "Z"),
        "vehicles": vehicles,
        "ingestedAt": "2026-01-01T00:00:00Z",
    }


def request(i=0, junction="1", vehicles=20):
    reading = TrafficReading.from_kafka_event(reading_event(i, junction, vehicles))
    return Request(task="traffic", timestamp=reading.reading_timestamp, data=reading)


# =========================================================
# FEATURES
# =========================================================
def test_features_for_matches_transform_rows():
    builder = TrafficFeatureBuilder(window_size=3, junction_ids=[1, 2])
    df = pd.DataFrame({
        "DateTime": pd.date_range("2017-03-06 06:00", periods=5, freq="h"),
        "Vehicles": [10, 11, 12, 13, 14],
        "Junction": 2,
    })

    X, y = builder.transform(df)

    assert X[-1].tolist() == builder.features_for([11, 12, 13], "2017-03-06 10:00", builder.junction_code(2))
    assert y[-1] == 14


def test_junction_code_accepts_text_ids_and_rejects_unknown():
    builder = TrafficFeatureBuilder(junction_ids=[1, 2, 3, 4])

    assert builder.junction_code("3") == builder.junction_code(3) == 2
    assert not builder.knows_junction("9")

    with pytest.raises(ValueError, match="Unknown junctions"):
        builder.junction_code("9")


# =========================================================
# ANOMALY DETECTOR
# =========================================================
def detector(**kwargs):
    return TrafficAnomalyDetector(
        z_threshold=3.0, min_relative_deviation=0.5, congestion_thresholds={"1": 89, "2": 30}, **kwargs
    )


@pytest.mark.parametrize("observed, expected, anomaly", [
    (80, 40, SURGE),
    (5, 40, DROP),
    (45, 40, None),
    (52, 40, None),
])
def test_observation_is_anomaly_only_when_far_in_both_measures(observed, expected, anomaly):
    assessment = detector().assess_observation(observed, expected, std=2.5)

    assert assessment.anomaly_type == anomaly
    assert assessment.is_anomaly == (anomaly is not None)


def test_quiet_junction_does_not_alarm_over_a_few_vehicles():
    assert not detector().assess_observation(observed=1.4, expected=1.0, std=0.1).is_anomaly


def test_zero_spread_never_flags():
    assert not detector().assess_observation(observed=500, expected=10, std=0).is_anomaly


def test_congestion_uses_the_junction_threshold():
    d = detector()

    assert d.congestion_expected("1", 89) and d.congestion_expected(2, 31.5)
    assert not d.congestion_expected("1", 88.9)
    assert not d.congestion_expected("7", 10_000)
    assert d.congestion_threshold("7") is None


# =========================================================
# INFERENCE (real weights)
# =========================================================
@pytest.fixture(scope="module")
def traffic_service():
    return RequestManager().traffic_anomaly_service


class RecordingModel:
    residual_std = 2.0

    def __init__(self):
        self.rows = []

    def predict(self, X):
        self.rows.append(np.asarray(X)[0].tolist())
        return np.array([50.0])

    def predict_mean_std(self, X):
        return np.array([50.0]), np.array([2.0])

    def predict_interval(self, X, confidence=0.95):
        return np.array([46.0]), np.array([54.0])


def test_predict_next_uses_latest_readings_and_the_next_hour(traffic_service, monkeypatch):
    model = RecordingModel()
    monkeypatch.setattr(traffic_service, "model", model)

    result = traffic_service.predict_next([1, 2, 3, 4, 5, 6, 7], pd.Timestamp("2017-03-06 08:00"), "3")

    # last six readings, hour 9, Monday, March, day 65, junction code 2
    assert model.rows[-1] == [2, 3, 4, 5, 6, 7, 9, 0, 3, 65, 2]
    assert result["target_timestamp"] == datetime(2017, 3, 6, 9)
    assert result["next_prediction"] == 50.0
    assert result["uncertainty"]["upper_95"] == 54.0
    assert result["model_version"] == "2026-06-14_13-36-24"


def test_handle_request_forecasts_forward_from_the_last_reading(traffic_service, monkeypatch):
    model = RecordingModel()
    monkeypatch.setattr(traffic_service, "model", model)

    df = pd.DataFrame({
        "DateTime": pd.date_range("2017-03-06 00:00", periods=8, freq="h"),
        "Vehicles": [1, 2, 3, 4, 5, 6, 7, 8],
        "Junction": 1,
    })

    result = traffic_service.handle_request(Request("traffic", T0, df))

    hours = [row[6] for row in model.rows[-traffic_service.horizon:]]
    assert hours[:3] == [8, 9, 10]
    assert model.rows[-traffic_service.horizon][:6] == [3, 4, 5, 6, 7, 8]
    assert model.rows[-traffic_service.horizon + 1][:6] == [4, 5, 6, 7, 8, 50.0]
    assert len(result["forecast"]) == traffic_service.horizon


def test_handle_request_rejects_mixed_junctions(traffic_service):
    df = pd.DataFrame({
        "DateTime": pd.date_range("2017-03-06", periods=8, freq="h"),
        "Vehicles": 5,
        "Junction": [1, 2] * 4,
    })

    with pytest.raises(ValueError, match="one junction"):
        traffic_service.handle_request(Request("traffic", T0, df))


def test_real_model_gives_finite_forecast_with_interval(traffic_service):
    result = traffic_service.predict_next([20, 22, 25, 30, 28, 26], T0, 1)

    assert np.isfinite(result["next_prediction"])
    assert result["uncertainty"]["lower_95"] < result["next_prediction"] < result["uncertainty"]["upper_95"]


# =========================================================
# PIPELINE
# =========================================================
class StubTrafficService:
    window_size = 3

    def __init__(self, prediction=40.0, std=2.5):
        self.prediction = prediction
        self.std = std
        self.calls = []

    def knows_junction(self, junction_id):
        return junction_id in ("1", "2")

    def predict_next(self, values, last_timestamp, junction_id):
        self.calls.append((list(values), last_timestamp, junction_id))
        return {
            "model_type": "xgboost",
            "model_version": "v1",
            "target_timestamp": last_timestamp + HOUR,
            "next_prediction": self.prediction,
            "uncertainty": {
                "mean": self.prediction,
                "std": self.std,
                "lower_95": self.prediction - 1.96 * self.std,
                "upper_95": self.prediction + 1.96 * self.std,
            },
        }


class RecordingProducer:
    def __init__(self, failures=0):
        self.failures = failures
        self.events = []

    def publish(self, key, event):
        if self.failures:
            self.failures -= 1
            raise PredictionPublishError("broker down")
        self.events.append((key, event))


def make_pipeline(service=None, producer=None):
    service = service or StubTrafficService()
    producer = producer or RecordingProducer()
    return TrafficPredictionPipeline(service, producer, detector()), service, producer


def feed(pipeline, vehicles, junction="1", start=0):
    for offset, value in enumerate(vehicles):
        pipeline.handle(request(start + offset, junction, value))


def test_first_event_has_forecast_but_no_observation():
    pipeline, service, producer = make_pipeline()

    feed(pipeline, [38, 39, 41])

    key, event = producer.events[0]
    assert key == "1" and len(producer.events) == 1
    assert event["junctionId"] == "1"
    assert event["observation"] is None
    assert event["predictions"][0]["targetTimestamp"] == "2017-03-06T09:00:00Z"
    assert event["predictions"][0]["predictedVehicles"] == 40.0
    assert event["predictions"][0]["congestionExpected"] is False
    assert event["predictions"][0]["congestionThreshold"] == 89.0
    json.dumps(event, allow_nan=False)


def test_normal_reading_is_not_an_anomaly():
    pipeline, _, producer = make_pipeline()

    feed(pipeline, [38, 39, 41, 42])

    observation = producer.events[-1][1]["observation"]
    assert observation["vehicles"] == 42.0 and observation["expectedVehicles"] == 40.0
    assert observation["anomaly"] is False and observation["anomalyType"] is None


def test_jam_is_reported_as_surge_anomaly():
    pipeline, _, producer = make_pipeline()

    feed(pipeline, [38, 39, 41, 95])

    observation = producer.events[-1][1]["observation"]
    assert observation["anomaly"] is True
    assert observation["anomalyType"] == "SURGE"
    assert observation["anomalyScore"] == 22.0
    assert observation["timestamp"] == "2017-03-06T09:00:00Z"


def test_traffic_that_stops_is_reported_as_drop_anomaly():
    pipeline, _, producer = make_pipeline()

    feed(pipeline, [38, 39, 41, 2])

    assert producer.events[-1][1]["observation"]["anomalyType"] == "DROP"


class ReactiveTrafficService(StubTrafficService):
    """Forecasts the last reading again, like a model that follows a jam."""

    def predict_next(self, values, last_timestamp, junction_id):
        self.prediction = float(values[-1])
        return super().predict_next(values, last_timestamp, junction_id)


def anomaly_types(producer):
    return [event["observation"]["anomalyType"] for _, event in producer.events if event["observation"]]


def test_return_to_normal_after_a_jam_is_not_a_new_anomaly():
    pipeline, _, producer = make_pipeline(service=ReactiveTrafficService())

    feed(pipeline, [40, 40, 40, 95, 41, 40])

    assert anomaly_types(producer) == ["SURGE", None, None]


def test_jam_that_continues_and_a_later_drop_are_still_reported():
    pipeline, _, producer = make_pipeline(service=ReactiveTrafficService())

    feed(pipeline, [40, 40, 40, 95, 200, 190, 3])

    assert anomaly_types(producer) == ["SURGE", "SURGE", None, "DROP"]


def test_forecast_above_threshold_means_congestion_expected():
    pipeline, _, producer = make_pipeline(service=StubTrafficService(prediction=93.0))

    feed(pipeline, [80, 85, 90])

    assert producer.events[-1][1]["predictions"][0]["congestionExpected"] is True


def test_junctions_do_not_share_history_or_forecasts():
    pipeline, service, producer = make_pipeline()

    feed(pipeline, [38, 39, 41], junction="1")
    feed(pipeline, [10, 11, 12, 95], junction="2")

    assert [key for key, _ in producer.events] == ["1", "2", "2"]
    assert service.calls[-1][0] == [11.0, 12.0, 95.0]


def test_unknown_junction_and_invalid_reading_are_skipped():
    pipeline, service, producer = make_pipeline()

    feed(pipeline, [38, 39, 41], junction="9")
    feed(pipeline, [-5, float("nan")], junction="1")

    assert service.calls == [] and producer.events == []


def test_retry_after_failed_publish_keeps_the_anomaly():
    pipeline, _, producer = make_pipeline(producer=RecordingProducer())

    feed(pipeline, [38, 39, 41])
    producer.failures = 1

    with pytest.raises(PredictionPublishError):
        pipeline.handle(request(3, "1", 95))

    pipeline.handle(request(3, "1", 95))

    assert len(producer.events) == 2
    assert producer.events[-1][1]["observation"]["anomalyType"] == "SURGE"


def test_reading_after_a_long_gap_is_not_compared_with_a_stale_forecast():
    pipeline, _, producer = make_pipeline()

    feed(pipeline, [38, 39, 41])
    pipeline.handle(request(30, "1", 95))

    assert len(producer.events) == 1


# =========================================================
# KAFKA WIRING
# =========================================================
def test_traffic_stream_has_its_own_topics_and_group():
    config_path = str(Path(__file__).resolve().parents[1] / "configs" / "kafka_config.json")

    electricity = KafkaConfigLoader.load(config_path)
    traffic = KafkaConfigLoader.load(config_path, stream="traffic")

    assert traffic.topic == "traffic.readings.raw"
    assert traffic.predictions_topic == "traffic.predictions.generated"
    assert traffic.group_id != electricity.group_id
    assert electricity.topic == "electricity.readings.raw"


class OneMessageConsumer:
    def __init__(self, payload):
        self.payload = payload
        self.committed = []
        self.owner = None

    def subscribe(self, topics):
        self.topics = topics

    def poll(self, timeout):
        if self.payload is None:
            self.owner.stop()
            return None
        payload, self.payload = self.payload, None
        return FakeMessage(payload)

    def commit(self, message, asynchronous):
        self.committed.append(message.offset())

    def close(self):
        pass


class FakeMessage:
    def __init__(self, payload):
        self.payload = payload

    def value(self):
        return self.payload

    def error(self):
        return None

    def offset(self):
        return 7

    def partition(self):
        return 0

    def topic(self):
        return "traffic.readings.raw"


def test_traffic_consumer_maps_events_to_traffic_requests():
    fake = OneMessageConsumer(json.dumps(reading_event(2, junction=3, vehicles=17)).encode())
    consumer = TrafficReadingKafkaConsumer(
        KafkaConsumerConfig("kafka:9092", "group", "traffic.readings.raw", "traffic.predictions.generated"),
        consumer=fake,
    )
    fake.owner = consumer
    handled = []

    consumer.start(handled.append)

    assert fake.topics == ["traffic.readings.raw"] and fake.committed == [7]
    assert handled[0].task == "traffic"
    assert handled[0].data.junction_id == "3"
    assert handled[0].data.vehicles == 17.0
    assert handled[0].data.reading_timestamp == T0 + 2 * HOUR
