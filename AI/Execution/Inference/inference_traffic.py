import logging
from pathlib import Path

import numpy as np
import pandas as pd

from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel
from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder

logger = logging.getLogger(__name__)

STEP = pd.Timedelta(hours=1)


class InferenceTrafficService:

    def __init__(self, config: dict):

        self.config = config

        self.model_type = config["model"]["type"].lower()
        self.model_path = config["model"]["path"]

        # Weights live in <model type>/<run timestamp>/model.joblib
        self.model_version = Path(self.model_path).parent.name

        # must match training
        self.window_size = config["features"]["window_size"]
        self.use_hour = config["features"]["use_hour"]
        self.use_day_of_week = config["features"]["use_day_of_week"]

        self.horizon = config["forecast"]["horizon"]

        self.model = self._load_model()

        # IMPORTANT: same feature builder as training
        self.builder = TrafficFeatureBuilder(
            window_size=self.window_size,
            use_hour=self.use_hour,
            use_day_of_week=self.use_day_of_week,
            junction_ids=config["features"].get("junction_ids"),
        )

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
    def knows_junction(self, junction_id) -> bool:
        return self.builder.knows_junction(junction_id)

    # ----------------------------
    def _build_input(self, values, target_time, junction_id):

        values = np.asarray(values, dtype=float)

        if len(values) < self.window_size:
            raise ValueError(
                f"Not enough data to build features. Need at least window_size={self.window_size} rows."
            )

        row = self.builder.features_for(
            values[-self.window_size:],
            target_time,
            self.builder.junction_code(junction_id),
        )

        return np.asarray(row, dtype=float).reshape(1, -1)

    # ----------------------------
    def _uncertainty(self, X):

        if self.model_type == "ngboost":
            dist = self.model.model.pred_dist(X)

            mean = float(np.asarray(dist.loc)[0])
            std = float(np.asarray(dist.scale)[0])
            lower = float(dist.ppf(0.025)[0])
            upper = float(dist.ppf(0.975)[0])

        else:
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
    def predict_next(self, values, last_timestamp, junction_id):
        """
        Forecast for the hour after `last_timestamp`, from the readings of one
        junction that end at `last_timestamp`.
        """

        target_time = pd.Timestamp(last_timestamp) + STEP

        X = self._build_input(values, target_time, junction_id)

        uncertainty = self._uncertainty(X)

        if self.model_type == "ngboost":
            next_pred = uncertainty["mean"]
        else:
            next_pred = float(self.model.predict(X)[0])

        return {
            "model_type": self.model_type,
            "model_version": self.model_version,
            "target_timestamp": target_time.to_pydatetime(),
            "next_prediction": next_pred,
            "uncertainty": uncertainty,
        }

    # ----------------------------
    def _forecast(self, values, last_timestamp, junction_id):
        """
        recursive multi-step forecast
        """

        window = list(np.asarray(values, dtype=float)[-self.window_size:])
        target_time = pd.Timestamp(last_timestamp)

        preds = []

        for _ in range(self.horizon):

            target_time = target_time + STEP

            X = self._build_input(window, target_time, junction_id)

            pred = float(self.model.predict(X)[0])
            preds.append(pred)

            window = window[1:] + [pred]

        return np.array(preds)

    # ----------------------------
    def handle_request(self, request):
        """
        request.data: rows of ONE junction with columns DateTime, Vehicles, Junction
        """

        logger.info(
            "Traffic inference request task=%s data_len=%s",
            request.task,
            len(request.data),
        )

        df = request.data.copy()
        df["DateTime"] = pd.to_datetime(df["DateTime"])
        df = df.sort_values("DateTime")

        junctions = df["Junction"].unique()

        if len(junctions) != 1:
            raise ValueError(f"Expected readings of one junction, got {sorted(junctions)}")

        values = df["Vehicles"].to_numpy(dtype=float)
        last_timestamp = df["DateTime"].iloc[-1]
        junction_id = junctions[0]

        result = {
            "task": request.task,
            "timestamp": request.timestamp.isoformat(),
            **self.predict_next(values, last_timestamp, junction_id),
            "forecast": self._forecast(values, last_timestamp, junction_id).tolist(),
        }

        logger.info("Traffic inference completed")

        return result
