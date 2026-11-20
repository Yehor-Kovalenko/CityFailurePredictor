import os
import threading
import uvicorn
from fastapi import FastAPI
from fastapi.responses import JSONResponse

import logging
from AI.Orchestration.kafka_config_loader import KafkaConfigLoader
from AI.Orchestration.kafka_consumer import ElectricityReadingKafkaConsumer
from AI.Orchestration.kafka_prediction_producer import PredictionKafkaProducer
from AI.Orchestration.prediction_pipeline import ElectricityPredictionPipeline
from AI.Orchestration.request_manager import RequestManager

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Service")

request_manager = RequestManager()
kafka_config = KafkaConfigLoader.load("AI/configs/kafka_config.json")
prediction_producer = PredictionKafkaProducer(kafka_config)

pipeline = ElectricityPredictionPipeline(
    energy_service=request_manager.energy_service,
    producer=prediction_producer,
)

consumer_failed = threading.Event()


@app.get("/health")
def health():
    if consumer_failed.is_set():
        return JSONResponse(
            status_code=503,
            content={"status": "DOWN", "service": "ai-service", "reason": "kafka consumer stopped"},
        )

    return {"status": "UP", "service": "ai-service"}


def handle_ai_request(request):
    pipeline.handle(request)


def start_consumer(on_failure=None):
    try:
        consumer = ElectricityReadingKafkaConsumer(kafka_config)
        consumer.start(handle_ai_request)
    except Exception:
        logger.critical("Kafka consumer stopped", exc_info=True)
        consumer_failed.set()

        if on_failure:
            on_failure()


def exit_process():
    # The container restarts and Kafka delivers the uncommitted message again.
    os._exit(1)


if __name__ == "__main__":
    consumer_thread = threading.Thread(
        target=start_consumer,
        kwargs={"on_failure": exit_process},
        daemon=True,
    )
    consumer_thread.start()

    uvicorn.run(app, host="0.0.0.0", port=8090)
