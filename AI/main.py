import threading
import uvicorn
from fastapi import FastAPI

import logging
from AI.Orchestration.kafka_config_loader import KafkaConfigLoader
from AI.Orchestration.kafka_consumer import ElectricityReadingKafkaConsumer
from AI.Orchestration.kafka_prediction_producer import PredictionKafkaProducer
from AI.Orchestration.prediction_event_mapper import PredictionEventMapper
from AI.Orchestration.request_manager import RequestManager, Request

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Service")

request_manager = RequestManager()
kafka_config = KafkaConfigLoader.load("AI/configs/kafka_config.json")
prediction_producer = PredictionKafkaProducer(kafka_config)

reading_history_by_household: dict[str, list[float]] = {}


@app.get("/health")
def health():
    return {"status": "UP", "service": "ai-service"}


def handle_ai_request(request):
    reading = request.data

    logger.info(
        "Received electricity reading household_id=%s timestamp=%s kwh=%s",
        reading.household_id,
        reading.reading_timestamp,
        reading.kwh,
    )

    history = reading_history_by_household.setdefault(reading.household_id, [])
    history.append(float(reading.kwh))

    window_size = request_manager.energy_service.window_size

    if len(history) > window_size:
        history[:] = history[-window_size:]

    if len(history) < window_size:
        logger.info(
            "Not enough history for prediction household_id=%s current=%s required=%s",
            reading.household_id,
            len(history),
            window_size,
        )
        return

    inference_request = Request(
        task="electricity",
        timestamp=reading.reading_timestamp,
        data=history,
    )

    result = request_manager.handle(inference_request)

    prediction_event = PredictionEventMapper.from_energy_result(
        reading=reading,
        inference_result=result,
    )

    prediction_producer.publish(
        key=reading.household_id,
        event=prediction_event,
    )

    logger.info(
        "Prediction published household_id=%s predicted_kwh=%s model=%s",
        reading.household_id,
        prediction_event["predictions"][0]["predictedKwh"],
        prediction_event["modelType"],
    )


def start_consumer():
    consumer = ElectricityReadingKafkaConsumer(kafka_config)
    consumer.start(handle_ai_request)


if __name__ == "__main__":
    consumer_thread = threading.Thread(target=start_consumer, daemon=True)
    consumer_thread.start()

    uvicorn.run(app, host="0.0.0.0", port=8090)
