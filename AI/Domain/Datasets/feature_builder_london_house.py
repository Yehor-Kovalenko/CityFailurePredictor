import pandas as pd


class FeatureLondonHouseBuilder:

    def __init__(self, window_size: int = 24):
        self.window_size = window_size

    def transform(self, df: pd.DataFrame):

        df = df.copy()
        df = df.sort_values("DateTime")

        values = df["KWH/hh (per half hour)"].values

        X, y = [], []

        for i in range(self.window_size, len(values)):

            X.append(values[i-self.window_size:i])
            y.append(values[i])

        return pd.DataFrame(X), pd.Series(y)