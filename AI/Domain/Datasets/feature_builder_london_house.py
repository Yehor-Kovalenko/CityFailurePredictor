import numpy as np
import pandas as pd

VALUE_COLUMN = "KWH/hh (per half hour)"
HOUSE_COLUMN = "LCLid"


class FeatureLondonHouseBuilder:

    def __init__(self, window_size: int = 24):
        self.window_size = window_size

    def transform(self, df: pd.DataFrame):

        if HOUSE_COLUMN in df.columns:
            groups = [group for _, group in df.groupby(HOUSE_COLUMN, sort=True)]
        else:
            groups = [df]

        X, y = [], []

        for group in groups:
            values = group.sort_values("DateTime")[VALUE_COLUMN].to_numpy(dtype=float)

            if len(values) <= self.window_size:
                continue

            windows = np.lib.stride_tricks.sliding_window_view(values, self.window_size + 1)

            X.append(windows[:, :-1])
            y.append(windows[:, -1])

        if not X:
            return pd.DataFrame(columns=range(self.window_size), dtype=float), pd.Series(dtype=float)

        return pd.DataFrame(np.concatenate(X)), pd.Series(np.concatenate(y))
