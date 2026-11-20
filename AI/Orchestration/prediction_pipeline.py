import logging
import math

from AI.Orchestration.prediction_event_mapper import PredictionEventMapper
from AI.Orchestration.reading_history import ReadingHistory

logger = logging.getLogger(__name__)


class ElectricityPredictionPipeline:
    """Turns one electricity reading into one published prediction event."""

    def __init__(self, energy_service, producer, history: ReadingHistory | None = None):
        self.energy_service = energy_service
        self.producer = producer
        self.history = history or ReadingHistory(window_size=energy_service.window_size)

    def handle(self, request) -> None:
        reading = request.data

        logger.info(
            "Received electricity reading household_id=%s timestamp=%s kwh=%s",
            reading.household_id,
            reading.reading_timestamp,
            reading.kwh,
        )

        window = self.history.add(
            reading.household_id,
            reading.reading_timestamp,
            float(reading.kwh),
        )

        if window is None:
            logger.info(
                "No prediction for household_id=%s history=%s required=%s",
                reading.household_id,
                self.history.size(reading.household_id),
                self.history.window_size,
            )
            return

        result = self.energy_service.predict_next(window)

        if not math.isfinite(result["next_prediction"]):
            logger.error(
                "Model returned a non-finite prediction for household_id=%s; nothing published",
                reading.household_id,
            )
            return

        prediction_event = PredictionEventMapper.from_energy_result(
            reading=reading,
            inference_result=result,
        )

        self.producer.publish(
            key=reading.household_id,
            event=prediction_event,
        )

        logger.info(
            "Prediction published household_id=%s predicted_kwh=%s model=%s version=%s",
            reading.household_id,
            prediction_event["predictions"][0]["predictedKwh"],
            prediction_event["modelType"],
            prediction_event["modelVersion"],
        )
