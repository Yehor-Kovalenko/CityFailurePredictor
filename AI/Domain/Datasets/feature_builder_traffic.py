import logging

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


class TrafficFeatureBuilder:

    def __init__(
        self,
        window_size: int = 24,
        use_hour: bool = True,
        use_day_of_week: bool = True,
        use_lag_24: bool = True,
        use_lag_168: bool = True,
        use_rolling_mean_24: bool = True,
    ):
        self.window_size = window_size

        self.use_hour = use_hour
        self.use_day_of_week = use_day_of_week

        self.use_lag_24 = use_lag_24
        self.use_lag_168 = use_lag_168

        self.use_rolling_mean_24 = use_rolling_mean_24

    def transform(self, df: pd.DataFrame):

        df = df.copy()

        df["DateTime"] = pd.to_datetime(df["DateTime"])

        df = df.sort_values(["Junction", "DateTime"])

        df["Junction"] = df["Junction"].astype("category").cat.codes

        features = []
        targets = []

        logger.info(
            "Building traffic features window_size=%s",
            self.window_size
        )

        for junction, group in df.groupby("Junction"):

            group = (
                group
                .sort_values("DateTime")
                .reset_index(drop=True)
            )

            vehicles = group["Vehicles"].values

            # determine how much history we need
            required_history = max(
                self.window_size,
                24 if self.use_lag_24 else 0,
                168 if self.use_lag_168 else 0,
            )

            for i in range(required_history, len(group)):

                current_time = group.loc[i, "DateTime"]

                row_features = []

                # --------------------
                # WINDOW FEATURES
                # --------------------

                window = vehicles[
                    i - self.window_size:i
                ]

                row_features.extend(window)

                # --------------------
                # CALENDAR FEATURES
                # --------------------

                if self.use_hour:
                    row_features.append(current_time.hour)

                if self.use_day_of_week:
                    row_features.append(current_time.dayofweek)

                row_features.append(current_time.month)
                row_features.append(current_time.dayofyear)

                # --------------------
                # LAG FEATURES
                # --------------------

                if self.use_lag_24:
                    row_features.append(
                        vehicles[i - 24]
                    )

                if self.use_lag_168:
                    row_features.append(
                        vehicles[i - 168]
                    )

                # --------------------
                # ROLLING FEATURES
                # --------------------

                if self.use_rolling_mean_24:
                    row_features.append(
                        np.mean(
                            vehicles[i - 24:i]
                        )
                    )

                # --------------------
                # JUNCTION
                # --------------------

                row_features.append(junction)

                features.append(row_features)

                targets.append(
                    vehicles[i]
                )

        X = np.asarray(features)
        y = np.asarray(targets)

        logger.info(
            "Feature building completed X_shape=%s y_shape=%s",
            X.shape,
            y.shape
        )

        return X, y