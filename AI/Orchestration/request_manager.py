import logging
from dataclasses import dataclass
from typing import Any, Optional
from datetime import datetime

from AI.Execution.Inference.inference_london_house import InferenceEnergyService
from AI.Orchestration.config_manager import ConfigManager

logger = logging.getLogger(__name__)

@dataclass
class Request:
    task: str
    timestamp: datetime
    data: Any


class RequestManager:

    def __init__(self):

        self.config_manager = ConfigManager()

        logger.info("Initializing RequestManager")

        energy_config = self.config_manager.resolve("electricity")

        self.energy_service = InferenceEnergyService(energy_config)

        logger.info("Energy service initialized model=%s", energy_config["model"]["type"])

    def handle(self, request):

        logger.info(
            "Received request task=%s timestamp=%s",
            request.task,
            request.timestamp,
        )

        if request.task == "electricity":

            logger.info("Starting electricity forecast")


            result = self.energy_service.handle_request(request)

            logger.info(
                "Inference completed task=%s next_prediction=%s",
                request.task,
                result.get("next_prediction"),
            )

            return result

        logger.error("Unsupported task=%s", request.task)
        raise ValueError(f"Unsupported task: {request.task}")