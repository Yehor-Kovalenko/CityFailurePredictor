import threading
import uvicorn
from fastapi import FastAPI

import logging

from AI.Orchestration.request_manager import RequestManager
from Orchestration.config_manager import ConfigManager
from Orchestration.kafka_config_loader import KafkaConfigLoader
from Orchestration.kafka_consumer import ElectricityReadingKafkaConsumer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Service")

request_manager = RequestManager()

@app.get("/health")
def health():
    return {"status": "UP", "service": "ai-service"}


def handle_ai_request(request):

    logger.info(
        "Received Kafka reading task=%s household_id=%s timestamp=%s kwh=%s",
        request.task,
        request.data,
        request.timestamp,
    )

    result = request_manager.handle(request)

    logger.info("Inference result=%s", result)

    return result

def start_consumer():
    kafka_config = KafkaConfigLoader.load("configs/kafka_config.json")
    consumer = ElectricityReadingKafkaConsumer(kafka_config)
    consumer.start(handle_ai_request)


if __name__ == "__main__":
    consumer_thread = threading.Thread(target=start_consumer, daemon=True)
    consumer_thread.start()

    uvicorn.run(app, host="0.0.0.0", port=8090)
