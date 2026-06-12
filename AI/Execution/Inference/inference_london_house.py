import json
import numpy as np
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional


from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel
from AI.Orchestration.request_manager import Request

import logging

logger = logging.getLogger(__name__)

# =========================================================
# REQUEST OBJECT
# =========================================================


# =========================================================
# INFERENCE SERVICE
# =========================================================
class InferenceEnergyService:

    def __init__(self, config: dict):

        self.config = config

        self.model_type = config["model"]["type"].lower()
        self.model_path = config["model"]["path"]

        self.window_size = config["features"]["window_size"]
        self.lookback_hours = config["features"]["lookback_hours"]
        self.horizon = config["features"]["horizon"]

        self.model = self._load_model()

    # ----------------------------
    def _load_model(self):

        if self.model_type == "ngboost":
            model = NGBoostModel()

        elif self.model_type == "xgboost":
            model = XGBoostModel()

        else:
            raise ValueError(f"Unsupported model type: {self.model_type}")

        model.load(self.model_path)
        return model

    # ----------------------------
    def _build_input(self, values):

        values = np.asarray(values)

        if len(values) < self.window_size:
            raise ValueError(
                f"Not enough history: {len(values)} < {self.window_size}"
            )

        return values[-self.window_size:].reshape(1, -1)

    # ----------------------------
    def _forecast(self, values):

        window = np.asarray(values[-self.window_size:]).copy()
        preds = []

        for _ in range(self.horizon):
            X = window.reshape(1, -1)
            pred = self.model.predict(X)[0]

            preds.append(pred)

            window = np.roll(window, -1)
            window[-1] = pred

        return np.array(preds)

    # ----------------------------
    def handle_request(self, request):

        logger.info(
            "Handling inference request task=%s timestamp=%s data_len=%s",
            request.task,
            request.timestamp,
            len(request.data),
        )

        values = np.asarray(request.data)

        X = self._build_input(values)

        next_pred = self.model.predict(X)[0]

        horizon_pred = self._forecast(values)

        logger.info("Prediction generated next=%s horizon_len=%s",
                    next_pred,
                    len(horizon_pred))

        uncertainty = None

        if self.model_type == "ngboost":
            logger.info("Computing NGBoost uncertainty")

            dist = self.model.model.pred_dist(X)

            mean = np.asarray(dist.loc)[0]
            std = np.asarray(dist.scale)[0]

            alpha = 0.05
            lower = dist.ppf(alpha / 2)
            upper = dist.ppf(1 - alpha / 2)

            uncertainty = {
                "mean": float(mean),
                "std": float(std),
                "lower_95": float(lower[0]),
                "upper_95": float(upper[0]),
            }

            logger.info(
                "Uncertainty computed mean=%s std=%s",
                mean,
                std,
            )

        result = {
            "task": request.task,
            "timestamp": request.timestamp.isoformat(),
            "model_type": self.model_type,
            "next_prediction": float(next_pred),
            "forecast": horizon_pred.tolist(),
            "uncertainty": uncertainty,
        }

        logger.info("Inference completed successfully")

        return result


## Potential use case example that it works
# if __name__ == "__main__":
#
#     service = InferenceEnergyService(
#         "../../Domain/Resources/Configs/Electricity/inference_config.json"
#     )
#
#     last_24h_values = np.random.rand(24)
#
#     request = Request(
#         task="electricity",
#         timestamp=datetime.utcnow(),
#         data=last_24h_values,
#     )
#
#     result = service.handle_request(request)
#     print(result)
