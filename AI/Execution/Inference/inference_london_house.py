import json
import numpy as np
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional


from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel
from AI.Orchestration.request_manager import Request


# =========================================================
# REQUEST OBJECT
# =========================================================


# =========================================================
# INFERENCE SERVICE
# =========================================================
class InferenceEnergyService:

    def __init__(self, config_path: str):

        with open(config_path, "r") as f:
            self.config = json.load(f)

        self.model_type = self.config["model"]["type"].lower()
        self.model_path = self.config["model"]["path"]

        self.window_size = self.config["features"]["window_size"]
        self.lookback_hours = self.config["features"]["lookback_hours"]
        self.horizon = self.config["features"]["horizon"]

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
    def _build_input(self, values, window_size):
        values = np.asarray(values)

        if len(values) < window_size:
            raise ValueError(
                f"Not enough history: {len(values)} < {window_size}"
            )

        return values[-window_size:].reshape(1, -1)

    # ----------------------------
    def _forecast(self, values, window_size, horizon):

        window = np.asarray(values[-window_size:]).copy()
        preds = []

        for _ in range(horizon):

            X = window.reshape(1, -1)
            pred = self.model.predict(X)[0]

            preds.append(pred)

            window = np.roll(window, -1)
            window[-1] = pred

        return np.array(preds)

    # ----------------------------
    def handle_request(self, request: Request):

        if request.task != "electricity_forecast":
            raise ValueError(f"Unsupported task: {request.task}")

        # ----------------------------
        # PARAM OVERRIDES
        # ----------------------------
        window_size = (
            request.params.get("window_size")
            if request.params else self.window_size
        )

        horizon = (
            request.params.get("horizon")
            if request.params else self.horizon
        )

        # ----------------------------
        # DATA
        # ----------------------------
        values = np.asarray(request.data)

        # ----------------------------
        # NEXT STEP PREDICTION
        # ----------------------------
        X = self._build_input(values, window_size)
        next_pred = self.model.predict(X)[0]

        # ----------------------------
        # HORIZON FORECAST
        # ----------------------------
        horizon_pred = self._forecast(values, window_size, horizon)

        # ----------------------------
        # NGBOOST OPTIONAL OUTPUT
        # ----------------------------
        uncertainty = None

        if self.model_type == "ngboost":
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

        # ----------------------------
        # RESPONSE
        # ----------------------------
        return {
            "task": request.task,
            "timestamp": request.timestamp.isoformat(),
            "model_type": self.model_type,
            "next_prediction": float(next_pred),
            "horizon": horizon,
            "forecast": horizon_pred.tolist(),
            "uncertainty": uncertainty
        }


if __name__ == "__main__":

    service = InferenceEnergyService(
        "../../Domain/Resources/LondonHouse/inference_config.json"
    )

    last_24h_values = np.random.rand(24)

    request = Request(
        task="electricity_forecast",
        timestamp=datetime.utcnow(),
        data=last_24h_values,
        params={
            "window_size": 24,
            "horizon": 48
        }
    )

    result = service.handle_request(request)
    print(result)
