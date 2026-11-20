import os
import threading
from contextlib import asynccontextmanager
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

import logging
from AI.Domain.Algorithms.VRPTW import InfeasibleInstanceError
from AI.Domain.Algorithms.traffic_anomaly import TrafficAnomalyDetector
from AI.Orchestration.kafka_config_loader import KafkaConfigLoader
from AI.Orchestration.kafka_consumer import ElectricityReadingKafkaConsumer, TrafficReadingKafkaConsumer
from AI.Orchestration.kafka_prediction_producer import PredictionKafkaProducer
from AI.Orchestration.prediction_pipeline import ElectricityPredictionPipeline
from AI.Orchestration.request_manager import RequestManager
from AI.Orchestration.routes_api import RouteOptimizationRequest, RouteOptimizationResponse, optimize_routes
from AI.Orchestration.traffic_pipeline import TrafficPredictionPipeline

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)

logger = logging.getLogger(__name__)

KAFKA_CONFIG_PATH = "AI/configs/kafka_config.json"

running_consumers = []
consumer_threads = []


@asynccontextmanager
async def lifespan(_app):
    yield

    # Leave the consumer groups on shutdown, so the next instance gets the
    # partitions at once instead of waiting for the session timeout.
    for consumer in list(running_consumers):
        consumer.stop()

    for thread in consumer_threads:
        thread.join(timeout=10)


app = FastAPI(title="AI Service", lifespan=lifespan)

request_manager = RequestManager()

kafka_config = KafkaConfigLoader.load(KAFKA_CONFIG_PATH)
prediction_producer = PredictionKafkaProducer(kafka_config)

pipeline = ElectricityPredictionPipeline(
    energy_service=request_manager.energy_service,
    producer=prediction_producer,
)

traffic_kafka_config = KafkaConfigLoader.load(KAFKA_CONFIG_PATH, stream="traffic")

traffic_pipeline = TrafficPredictionPipeline(
    traffic_service=request_manager.traffic_anomaly_service,
    producer=PredictionKafkaProducer(traffic_kafka_config),
    detector=TrafficAnomalyDetector(**request_manager.traffic_anomaly_service.config.get("anomaly", {})),
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


@app.post("/routes/optimize", response_model=RouteOptimizationResponse)
def routes_optimize(body: RouteOptimizationRequest):
    try:
        return optimize_routes(request_manager, body)
    except InfeasibleInstanceError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


def handle_ai_request(request):
    pipeline.handle(request)


def handle_traffic_request(request):
    traffic_pipeline.handle(request)


def _run_consumer(name, build_consumer, handler, on_failure):
    try:
        consumer = build_consumer()
        running_consumers.append(consumer)
        consumer.start(handler)
    except Exception:
        logger.critical("Kafka consumer stopped stream=%s", name, exc_info=True)
        consumer_failed.set()

        if on_failure:
            on_failure()


def start_consumer(on_failure=None):
    _run_consumer(
        "electricity",
        lambda: ElectricityReadingKafkaConsumer(kafka_config),
        handle_ai_request,
        on_failure,
    )


def start_traffic_consumer(on_failure=None):
    _run_consumer(
        "traffic",
        lambda: TrafficReadingKafkaConsumer(traffic_kafka_config),
        handle_traffic_request,
        on_failure,
    )


def exit_process():
    # The container restarts and Kafka delivers the uncommitted message again.
    os._exit(1)


if __name__ == "__main__":
    for target in (start_consumer, start_traffic_consumer):
        thread = threading.Thread(
            target=target,
            kwargs={"on_failure": exit_process},
            daemon=True,
        )
        consumer_threads.append(thread)
        thread.start()

    uvicorn.run(app, host="0.0.0.0", port=8090)
