import json
from datetime import datetime, timedelta, timezone

import numpy as np
import pytest
from confluent_kafka import KafkaError, KafkaException

from AI.Domain.Datasets.electricity_reading import ElectricityReading
from AI.Orchestration.kafka_consumer import (
    ElectricityReadingKafkaConsumer,
    KafkaConsumerConfig,
    MessageProcessingError,
)
from AI.Orchestration.kafka_prediction_producer import PredictionKafkaProducer, PredictionPublishError
from AI.Orchestration.prediction_event_mapper import PredictionEventMapper
from AI.Orchestration.prediction_pipeline import ElectricityPredictionPipeline
from AI.Orchestration.reading_history import ReadingHistory
from AI.Orchestration.request_manager import Request, RequestManager

T0 = datetime(2013, 1, 1, tzinfo=timezone.utc)
HALF_HOUR = timedelta(minutes=30)


def reading_event(i=0, household="MAC1", kwh=0.5):
    return {
        "eventId": f"event-{i}",
        "source": "test",
        "householdId": household,
        "tariffType": "Std",
        "readingTimestamp": (T0 + i * HALF_HOUR).isoformat().replace("+00:00", "Z"),
        "kwh": kwh,
        "ingestedAt": "2026-01-01T00:00:00Z",
    }


def reading(i=0, household="MAC1", kwh=0.5):
    return ElectricityReading.from_kafka_event(reading_event(i, household, kwh))


def request(i=0, household="MAC1", kwh=0.5):
    r = reading(i, household, kwh)
    return Request(task="electricity", timestamp=r.reading_timestamp, data=r)


# =========================================================
# KAFKA CONSUMER
# =========================================================
class FakeMessage:
    def __init__(self, offset, value=None, error=None):
        self._offset = offset
        self._value = value
        self._error = error

    def value(self):
        return self._value

    def error(self):
        return self._error

    def offset(self):
        return self._offset

    def partition(self):
        return 0

    def topic(self):
        return "electricity.readings.raw"


class FakeConsumer:
    def __init__(self, messages):
        self.messages = list(messages)
        self.committed = []
        self.closed = False
        self.owner = None

    def subscribe(self, topics):
        self.topics = topics

    def poll(self, timeout):
        if not self.messages:
            self.owner.stop()
            return None
        return self.messages.pop(0)

    def commit(self, message, asynchronous):
        self.committed.append(message.offset())

    def close(self):
        self.closed = True


def make_consumer(messages, **config):
    fake = FakeConsumer(messages)
    consumer = ElectricityReadingKafkaConsumer(
        KafkaConsumerConfig("kafka:9092", "group", "electricity.readings.raw", "predictions.generated",
                            retry_backoff_seconds=0, **config),
        consumer=fake,
    )
    fake.owner = consumer
    return consumer, fake


def valid(offset):
    return FakeMessage(offset, json.dumps(reading_event(offset)).encode())


def test_offsets_are_committed_after_successful_handling():
    consumer, fake = make_consumer([valid(0), valid(1)])
    handled = []

    consumer.start(lambda r: handled.append(r.data.event_id))

    assert handled == ["event-0", "event-1"]
    assert fake.committed == [0, 1]
    assert fake.closed


def test_failing_message_is_never_committed_and_later_ones_are_not_processed():
    consumer, fake = make_consumer([valid(0), valid(1), valid(2)], max_retries=3)
    attempts = []

    def handler(r):
        attempts.append(r.data.event_id)
        if r.data.event_id == "event-1":
            raise RuntimeError("broker down")

    with pytest.raises(MessageProcessingError, match="offset=1"):
        consumer.start(handler)

    assert attempts == ["event-0", "event-1", "event-1", "event-1"]
    assert fake.committed == [0]
    assert fake.closed


def test_transient_failure_is_retried_and_then_committed():
    consumer, fake = make_consumer([valid(0)], max_retries=3)
    attempts = []

    def handler(r):
        attempts.append(1)
        if len(attempts) < 3:
            raise RuntimeError("temporary")

    consumer.start(handler)

    assert len(attempts) == 3
    assert fake.committed == [0]


@pytest.mark.parametrize("payload", [b"not json", b'{"eventId": "x"}', b"[1, 2]", b"\xff\xfe"])
def test_malformed_message_is_skipped_without_blocking_the_partition(payload):
    consumer, fake = make_consumer([FakeMessage(0, payload), valid(1)])
    handled = []

    consumer.start(lambda r: handled.append(r.data.event_id))

    assert handled == ["event-1"]
    assert fake.committed == [0, 1]
    assert consumer.skipped_messages == 1


def test_fatal_broker_error_stops_the_consumer():
    consumer, fake = make_consumer([FakeMessage(0, error=KafkaError(KafkaError._ALL_BROKERS_DOWN))])

    with pytest.raises(KafkaException):
        consumer.start(lambda r: None)

    assert fake.closed


# =========================================================
# KAFKA PRODUCER
# =========================================================
class FakeProducer:
    def __init__(self, delivery_error=None, undelivered=0):
        self.delivery_error = delivery_error
        self.undelivered = undelivered
        self.sent = []
        self.callback = None

    def produce(self, topic, key, value, callback):
        self.sent.append((topic, key, value))
        self.callback = callback

    def flush(self, timeout):
        if not self.undelivered:
            self.callback(self.delivery_error, FakeMessage(0))
        return self.undelivered


def make_producer(**kwargs):
    fake = FakeProducer(**kwargs)
    config = KafkaConsumerConfig("kafka:9092", "group", "in", "predictions.generated")
    return PredictionKafkaProducer(config, producer=fake), fake


def test_publish_sends_json_to_predictions_topic():
    producer, fake = make_producer()

    producer.publish("MAC1", {"a": 1})

    assert fake.sent == [("predictions.generated", b"MAC1", b'{"a": 1}')]


def test_publish_raises_when_broker_rejects_the_message():
    producer, _ = make_producer(delivery_error="Broker: Message timed out")

    with pytest.raises(PredictionPublishError, match="timed out"):
        producer.publish("MAC1", {"a": 1})


def test_publish_raises_when_delivery_is_not_confirmed():
    producer, _ = make_producer(undelivered=1)

    with pytest.raises(PredictionPublishError, match="not confirmed"):
        producer.publish("MAC1", {"a": 1})


def test_publish_refuses_nan_because_java_cannot_parse_it():
    producer, fake = make_producer()

    with pytest.raises(ValueError):
        producer.publish("MAC1", {"predictedKwh": float("nan")})

    assert fake.sent == []


# =========================================================
# READING HISTORY
# =========================================================
def fill(history, n, household="MAC1", start=0, kwh=1.0):
    window = None
    for i in range(start, start + n):
        window = history.add(household, T0 + i * HALF_HOUR, kwh)
    return window


def test_window_is_returned_only_when_full_and_then_slides():
    history = ReadingHistory(window_size=3)

    assert history.add("MAC1", T0, 1.0) is None
    assert history.add("MAC1", T0 + HALF_HOUR, 2.0) is None
    assert history.add("MAC1", T0 + 2 * HALF_HOUR, 3.0) == [1.0, 2.0, 3.0]
    assert history.add("MAC1", T0 + 3 * HALF_HOUR, 4.0) == [2.0, 3.0, 4.0]


def test_households_are_independent():
    history = ReadingHistory(window_size=2)
    fill(history, 2, household="A", kwh=1.0)

    assert history.add("B", T0, 9.0) is None
    assert history.size("A") == 2


def test_repeated_timestamp_returns_same_window_without_adding_a_value():
    history = ReadingHistory(window_size=3)
    window = fill(history, 3)

    assert history.add("MAC1", T0 + 2 * HALF_HOUR, 99.0) == window


def test_out_of_order_reading_is_ignored():
    history = ReadingHistory(window_size=3)
    fill(history, 3)

    assert history.add("MAC1", T0, 99.0) is None
    assert history.add("MAC1", T0 + 3 * HALF_HOUR, 1.0) == [1.0, 1.0, 1.0]


def test_short_gap_is_interpolated():
    history = ReadingHistory(window_size=4)
    history.add("MAC1", T0, 1.0)

    window = history.add("MAC1", T0 + 3 * HALF_HOUR, 4.0)

    assert window == pytest.approx([1.0, 2.0, 3.0, 4.0])


def test_long_gap_starts_history_over():
    history = ReadingHistory(window_size=3, max_gap_steps=4)
    fill(history, 3)

    assert history.add("MAC1", T0 + 20 * HALF_HOUR, 5.0) is None
    assert history.size("MAC1") == 1


def test_non_finite_reading_is_ignored():
    history = ReadingHistory(window_size=1)

    assert history.add("MAC1", T0, float("nan")) is None
    assert history.size("MAC1") == 0


# =========================================================
# PREDICTION PIPELINE
# =========================================================
class StubEnergyService:
    window_size = 3

    def __init__(self, prediction=0.7, uncertainty=None):
        self.prediction = prediction
        self.uncertainty = uncertainty
        self.windows = []

    def predict_next(self, values):
        self.windows.append(list(values))
        return {
            "model_type": "xgboost",
            "model_version": "2026-06-11_21-12-19",
            "next_prediction": self.prediction,
            "uncertainty": self.uncertainty,
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


def test_pipeline_publishes_once_history_is_full():
    service, producer = StubEnergyService(), RecordingProducer()
    pipeline = ElectricityPredictionPipeline(service, producer)

    for i in range(4):
        pipeline.handle(request(i, kwh=float(i)))

    assert service.windows == [[0.0, 1.0, 2.0], [1.0, 2.0, 3.0]]
    assert [key for key, _ in producer.events] == ["MAC1", "MAC1"]

    event = producer.events[-1][1]
    assert event["predictionBatchId"] == "event-3"
    assert event["modelVersion"] == "2026-06-11_21-12-19"
    assert event["predictions"][0]["targetTimestamp"] == "2013-01-01T02:00:00Z"


def test_retry_after_failed_publish_predicts_from_the_same_window():
    service, producer = StubEnergyService(), RecordingProducer(failures=1)
    pipeline = ElectricityPredictionPipeline(service, producer)

    for i in range(2):
        pipeline.handle(request(i))

    with pytest.raises(PredictionPublishError):
        pipeline.handle(request(2, kwh=3.0))

    pipeline.handle(request(2, kwh=3.0))

    assert service.windows[-1] == service.windows[-2] == [0.5, 0.5, 3.0]
    assert len(producer.events) == 1


def test_non_finite_prediction_is_not_published():
    producer = RecordingProducer()
    pipeline = ElectricityPredictionPipeline(StubEnergyService(prediction=float("nan")), producer)

    for i in range(3):
        pipeline.handle(request(i))

    assert producer.events == []


# =========================================================
# EVENT MAPPER
# =========================================================
def test_negative_prediction_and_bounds_are_clipped_to_zero():
    result = {
        "model_type": "xgboost",
        "model_version": "v",
        "next_prediction": -0.02,
        "uncertainty": {"mean": -0.02, "std": 0.07, "lower_95": -0.16, "upper_95": 0.12},
    }

    prediction = PredictionEventMapper.from_energy_result(reading(), result)["predictions"][0]

    assert prediction["predictedKwh"] == 0.0
    assert prediction["lowerBoundKwh"] == 0.0
    assert prediction["upperBoundKwh"] == 0.12
    assert prediction["confidence"] == 0.95


def test_event_is_valid_strict_json_with_generation_time():
    result = {"model_type": "xgboost", "model_version": "v", "next_prediction": 0.4, "uncertainty": None}

    event = PredictionEventMapper.from_energy_result(reading(), result)

    json.dumps(event, allow_nan=False)
    generated = datetime.fromisoformat(event["generatedAt"].replace("Z", "+00:00"))
    assert abs(datetime.now(timezone.utc) - generated) < timedelta(minutes=1)
    assert event["predictions"][0]["confidence"] is None


# =========================================================
# ENERGY INFERENCE (real weights)
# =========================================================
@pytest.fixture(scope="module")
def energy_service():
    return RequestManager().energy_service


def test_predict_next_reports_interval_and_model_version(energy_service):
    result = energy_service.predict_next(np.full(energy_service.window_size, 0.3))

    assert np.isfinite(result["next_prediction"])
    assert result["model_version"] == "2026-06-11_21-12-19"
    assert result["uncertainty"]["lower_95"] < result["next_prediction"] < result["uncertainty"]["upper_95"]


def test_predict_next_matches_full_request(energy_service):
    values = np.linspace(0.1, 0.9, energy_service.window_size)

    full = energy_service.handle_request(Request("electricity", T0, values))

    assert full["next_prediction"] == energy_service.predict_next(values)["next_prediction"]
    assert len(full["forecast"]) == energy_service.horizon


def test_predict_next_rejects_short_history(energy_service):
    with pytest.raises(ValueError):
        energy_service.predict_next([0.1, 0.2])
