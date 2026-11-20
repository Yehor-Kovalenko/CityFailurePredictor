from pathlib import Path

import numpy as np


from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel

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

        # Weights live in <model type>/<run timestamp>/model.joblib
        self.model_version = Path(self.model_path).parent.name

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
    def _uncertainty(self, X):

        if self.model_type == "ngboost":
            dist = self.model.model.pred_dist(X)

            mean = float(np.asarray(dist.loc)[0])
            std = float(np.asarray(dist.scale)[0])
            lower = float(dist.ppf(0.025)[0])
            upper = float(dist.ppf(0.975)[0])

        else:
            # XGBoost has no predictive distribution; the interval comes from
            # the residual spread stored with the weights.
            if getattr(self.model, "residual_std", None) is None:
                return None

            mean_arr, std_arr = self.model.predict_mean_std(X)
            lower_arr, upper_arr = self.model.predict_interval(X, confidence=0.95)

            mean, std = float(mean_arr[0]), float(std_arr[0])
            lower, upper = float(lower_arr[0]), float(upper_arr[0])

        return {
            "mean": mean,
            "std": std,
            "lower_95": lower,
            "upper_95": upper,
        }

    # ----------------------------
    def predict_next(self, values):
        """One-step prediction with its 95% interval; used for every Kafka reading."""

        X = self._build_input(values)

        uncertainty = self._uncertainty(X)

        if self.model_type == "ngboost":
            next_pred = uncertainty["mean"]
        else:
            next_pred = float(self.model.predict(X)[0])

        return {
            "model_type": self.model_type,
            "model_version": self.model_version,
            "next_prediction": next_pred,
            "uncertainty": uncertainty,
        }

    # ----------------------------
    def handle_request(self, request):

        logger.info(
            "Handling inference request task=%s timestamp=%s data_len=%s",
            request.task,
            request.timestamp,
            len(request.data),
        )

        values = np.asarray(request.data)

        result = {
            "task": request.task,
            "timestamp": request.timestamp.isoformat(),
            **self.predict_next(values),
            "forecast": self._forecast(values).tolist(),
        }

        logger.info("Inference completed successfully next=%s", result["next_prediction"])

        return result


# # Potential use case example that it works
# if __name__ == "__main__":
#
#     config_path = "../../Domain/Resources/Configs/electricity/inference_config.json"
#
#     with open(config_path, "r") as f:
#         config = json.load(f)
#
#     service = InferenceEnergyService(config)
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
