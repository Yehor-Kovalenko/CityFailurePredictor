import json

import numpy as np
import logging
from datetime import datetime

import pandas as pd

from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel
from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder

logger = logging.getLogger(__name__)


class InferenceTrafficService:

    def __init__(self, config: dict):

        self.config = config

        self.model_type = config["model"]["type"].lower()
        self.model_path = config["model"]["path"]

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
    def _prepare_input(self, df):
        """
        df must be LAST N rows of ONE junction
        with columns: DateTime, Vehicles, Junction
        """

        X, _ = self.builder.transform(df)

        if len(X) == 0:
            raise ValueError(
                f"Not enough data to build features. Need at least window_size={self.window_size} rows."
            )

        return X[-1].reshape(1, -1)

    # ----------------------------
    def _forecast(self, df):
        """
        recursive multi-step forecast
        """

        df = df.copy()

        preds = []

        for _ in range(self.horizon):

            X, _ = self.builder.transform(df)

            if len(X) == 0:
                raise ValueError(
                    f"Not enough data during forecasting. Need at least window_size={self.window_size} rows."
                )

            x_last = X[-1].reshape(1, -1)

            pred = self.model.predict(x_last)[0]
            preds.append(pred)

            # append predicted row (fake next timestamp)
            next_time = df["DateTime"].iloc[-1] + pd.Timedelta(hours=1)

            new_row = {
                "DateTime": next_time,
                "Vehicles": pred,
                "Junction": df["Junction"].iloc[0]
            }

            df = pd.concat([df, pd.DataFrame([new_row])], ignore_index=True)

        return np.array(preds)

    # ----------------------------
    def handle_request(self, request):

        logger.info(
            "Traffic inference request task=%s data_len=%s",
            request.task,
            len(request.data),
        )

        df = request.data  # MUST be dataframe

        X = self._prepare_input(df)
        next_pred = self.model.predict(X)[0]

        horizon_pred = self._forecast(df)

        result = {
            "task": request.task,
            "timestamp": request.timestamp.isoformat(),
            "model_type": self.model_type,
            "next_prediction": float(next_pred),
            "forecast": horizon_pred.tolist(),
        }

        logger.info("Traffic inference completed")

        return result




# def main():
#
#     # ----------------------------
#     # LOAD CONFIG
#     # ----------------------------
#     config_path = "../../Domain/Resources/Configs/traffic/inference_config.json"
#
#     with open(config_path, "r") as f:
#         config = json.load(f)
#
#     # ----------------------------
#     # INIT SERVICE
#     # ----------------------------
#     service = InferenceTrafficService(config)
#
#     # ----------------------------
#     # LOAD SOME FAKE / REAL DATA
#     # ----------------------------
#     df = pd.read_csv("/home/agata/Documents/8/a lot of data/traffic.csv")
#
#     df["DateTime"] = pd.to_datetime(df["DateTime"])
#     df = df.sort_values(["Junction", "DateTime"])
#
#     # pick ONE junction (IMPORTANT)
#     junction_id = df["Junction"].iloc[0]
#     df_junction = df[df["Junction"] == junction_id].copy()
#
#     # take last 48 rows as history
#     last_history = df_junction.tail(48)
#
#     # ----------------------------
#     # CREATE REQUEST
#     # ----------------------------
#     request = Request(
#         task="traffic",
#         timestamp=datetime.utcnow(),
#         data=last_history
#     )
#
#     # ----------------------------
#     # RUN INFERENCE
#     # ----------------------------
#     result = service.handle_request(request)
#
#     # ----------------------------
#     # PRINT RESULT
#     # ----------------------------
#     print("\n=== RESULT ===")
#     print(result)
#
#
# if __name__ == "__main__":
#     main()