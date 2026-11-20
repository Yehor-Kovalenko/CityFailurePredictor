import logging
import math
from datetime import timedelta

from AI.Domain.Algorithms.traffic_anomaly import TrafficAnomalyDetector
from AI.Orchestration.prediction_event_mapper import PredictionEventMapper, to_java_instant
from AI.Orchestration.reading_history import ReadingHistory

logger = logging.getLogger(__name__)

HOUR = timedelta(hours=1)


class TrafficPredictionPipeline:
    """
    Turns one traffic reading into one published event that says:

    - whether the reading itself is an anomaly compared with the forecast that
      was made for its hour,
    - what is expected in the next hour and whether that means congestion.
    """

    def __init__(self, traffic_service, producer, detector: TrafficAnomalyDetector, history: ReadingHistory | None = None):
        self.traffic_service = traffic_service
        self.producer = producer
        self.detector = detector
        self.history = history or ReadingHistory(window_size=traffic_service.window_size, step=HOUR)

        # junction id -> forecast made for the next expected reading
        self._forecasts: dict[str, dict] = {}

    def handle(self, request) -> None:
        reading = request.data
        junction_id = reading.junction_id

        logger.info(
            "Received traffic reading junction_id=%s timestamp=%s vehicles=%s",
            junction_id,
            reading.reading_timestamp,
            reading.vehicles,
        )

        if not self.traffic_service.knows_junction(junction_id):
            logger.warning("Ignoring reading of junction_id=%s: the model was not trained on it", junction_id)
            return

        if not math.isfinite(reading.vehicles) or reading.vehicles < 0:
            logger.warning("Ignoring invalid reading junction_id=%s vehicles=%s", junction_id, reading.vehicles)
            return

        observation = self._assess_observation(reading)

        window = self.history.add(junction_id, reading.reading_timestamp, float(reading.vehicles))

        if window is None:
            logger.info(
                "No prediction for junction_id=%s history=%s required=%s",
                junction_id,
                self.history.size(junction_id),
                self.history.window_size,
            )
            return

        result = self.traffic_service.predict_next(window, reading.reading_timestamp, junction_id)

        if not math.isfinite(result["next_prediction"]):
            logger.error("Model returned a non-finite prediction for junction_id=%s; nothing published", junction_id)
            return

        predicted = max(0.0, result["next_prediction"])

        congestion = {
            "threshold": self.detector.congestion_threshold(junction_id),
            "expected": self.detector.congestion_expected(junction_id, predicted),
        }

        event = PredictionEventMapper.from_traffic_result(
            reading=reading,
            inference_result=result,
            observation=observation,
            congestion=congestion,
        )

        self.producer.publish(key=junction_id, event=event)

        # Stored only after a successful publish, so a retry of this reading is
        # still compared with the forecast made for it.
        self._forecasts[junction_id] = result

        logger.info(
            "Traffic prediction published junction_id=%s predicted=%.1f congestion=%s anomaly=%s",
            junction_id,
            predicted,
            congestion["expected"],
            observation["anomalyType"] if observation else None,
        )

    def _assess_observation(self, reading) -> dict | None:
        forecast = self._forecasts.get(reading.junction_id)

        if forecast is None or forecast["target_timestamp"] != reading.reading_timestamp:
            return None

        uncertainty = forecast.get("uncertainty")

        if not uncertainty:
            return None

        expected = max(0.0, forecast["next_prediction"])
        assessment = self.detector.assess_observation(reading.vehicles, expected, uncertainty["std"])

        return {
            "timestamp": to_java_instant(reading.reading_timestamp),
            "vehicles": float(reading.vehicles),
            "expectedVehicles": expected,
            "lowerBoundVehicles": max(0.0, uncertainty["lower_95"]),
            "upperBoundVehicles": max(0.0, uncertainty["upper_95"]),
            "anomaly": assessment.is_anomaly,
            "anomalyType": assessment.anomaly_type,
            "anomalyScore": round(assessment.score, 3),
        }
