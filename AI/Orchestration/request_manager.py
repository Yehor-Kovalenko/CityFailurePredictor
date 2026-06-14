import logging
from dataclasses import dataclass
from typing import Any, Optional
from datetime import datetime

from AI.Execution.Inference.inference_energy import InferenceEnergyService
from AI.Execution.Inference.inference_traffic import InferenceTrafficService
from AI.Execution.Inference.inference_vrp import InferenceRoutesService
from AI.Orchestration.config_manager import ConfigManager

logger = logging.getLogger(__name__)

@dataclass
class Request:
    task: str
    timestamp: datetime
    data: Any


class RequestManager:

    def __init__(self):

        logger.info("Initializing RequestManager")

        self.config_manager = ConfigManager()

        # -------------------------
        # Electricity service
        # -------------------------
        energy_config = self.config_manager.resolve("electricity")
        self.energy_service = InferenceEnergyService(energy_config)

        logger.info(
            "Energy service initialized model=%s",
            energy_config["model"]["type"]
        )

        # -------------------------
        # Traffic anomaly service
        # -------------------------
        traffic_anomaly_config = self.config_manager.resolve("traffic")
        self.traffic_anomaly_service = InferenceTrafficService(traffic_anomaly_config)

        logger.info(
            "Traffic service initialized model=%s",
            traffic_anomaly_config["model"]["type"]
        )

        # -------------------------
        # Routes (VRP) service
        # -------------------------
        routes_config = self.config_manager.resolve("vrp")
        self.routes_service = InferenceRoutesService(routes_config)

        logger.info("Routes service initialized")


    def handle(self, request):

        logger.info(
            "Received request task=%s timestamp=%s",
            request.task,
            request.timestamp,
        )

        # -------------------------
        # Electricity
        # -------------------------
        if request.task == "electricity":

            logger.info("Starting electricity forecast")

            result = self.energy_service.handle_request(request)

            logger.info(
                "Inference completed task=%s next_prediction=%s",
                request.task,
                result.get("next_prediction"),
            )

            return result

        # -------------------------
        # Traffic anomaly
        # -------------------------
        elif request.task == "traffic":

            logger.info("Starting traffic anomaly detection")

            return self.traffic_anomaly_service.handle_request(request)

        # -------------------------
        # VRP routes
        # -------------------------
        elif request.task == "vrp":

            logger.info("Starting VRP optimization")

            return self.routes_service.handle_request(request)

        # -------------------------
        # Unknown task
        # -------------------------
        else:
            logger.error("Unsupported task=%s", request.task)
            raise ValueError(f"Unsupported task: {request.task}")