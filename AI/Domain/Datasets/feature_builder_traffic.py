import numpy as np
import pandas as pd


class TrafficFeatureBuilder:

    def __init__(
            self,
            window_size: int = 6,
            use_hour: bool = True,
            use_day_of_week: bool = True,
    ):
        self.window_size = window_size
        self.use_hour = use_hour
        self.use_day_of_week = use_day_of_week

    def transform(self, df: pd.DataFrame):

        df = df.copy()
        df["DateTime"] = pd.to_datetime(df["DateTime"])
        df = df.sort_values(["Junction", "DateTime"])
        df["Junction"] = df["Junction"].astype("category").cat.codes

        X, y = [], []

        for junction, group in df.groupby("Junction"):

            group = group.sort_values("DateTime").reset_index(drop=True)
            vehicles = group["Vehicles"].values

            for i in range(self.window_size, len(group)):

                t = group.loc[i, "DateTime"]

                row = []

                # window
                row.extend(vehicles[i - self.window_size:i])

                # time features (KEEP EXACT ORDER)
                if self.use_hour:
                    row.append(t.hour)

                if self.use_day_of_week:
                    row.append(t.dayofweek)

                row.append(t.month)
                row.append(t.dayofyear)

                # junction LAST
                row.append(junction)

                X.append(row)
                y.append(vehicles[i])

        return np.array(X), np.array(y)
