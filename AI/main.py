import threading
import uvicorn
from fastapi import FastAPI

import logging
from Orchestration.config_manager import ConfigManager
from Orchestration.kafka_config_loader import KafkaConfigLoader
from Orchestration.kafka_consumer import ElectricityReadingKafkaConsumer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Service")

config_manager = ConfigManager("configs/configs.json")


@app.get("/health")
def health():
    return {"status": "UP", "service": "ai-service"}


def handle_ai_request(request):
    logger.info(
        "Received Kafka reading task=%s household_id=%s timestamp=%s kwh=%s",
        request.task,
        request.data.household_id,
        request.timestamp,
        request.data.kwh
    )

    model_config = config_manager.get_config(request.task)

    logger.info(
        "Selected model type=%s version=%s",
        model_config.model_type,
        model_config.model_version
    )

    # TODO: inference/training


def start_consumer():
    kafka_config = KafkaConfigLoader.load("configs/kafka_config.json")
    consumer = ElectricityReadingKafkaConsumer(kafka_config)
    consumer.start(handle_ai_request)


if __name__ == "__main__":
    consumer_thread = threading.Thread(target=start_consumer, daemon=True)
    consumer_thread.start()

    uvicorn.run(app, host="0.0.0.0", port=8090)
