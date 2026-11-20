import numpy as np
import pandas as pd


class TrafficFeatureBuilder:

    def __init__(
            self,
            window_size: int = 6,
            use_hour: bool = True,
            use_day_of_week: bool = True,
            junction_ids=None,
    ):
        """
        junction_ids: every junction the model was trained on. The junction
        feature is the position of the junction in this sorted list, so a
        request that carries a single junction is encoded the same way as in
        training. Without it the encoding is derived from the rows passed to
        transform(), which is only correct for the full training set.
        """
        self.window_size = window_size
        self.use_hour = use_hour
        self.use_day_of_week = use_day_of_week
        self.junction_ids = sorted(junction_ids) if junction_ids is not None else None

    def knows_junction(self, junction) -> bool:
        return self.junction_ids is None or self._code_of(junction) is not None

    def junction_code(self, junction) -> int:
        """Value of the junction feature for one junction id."""

        if self.junction_ids is None:
            return 0

        code = self._code_of(junction)

        if code is None:
            raise ValueError(
                f"Unknown junctions [{junction!r}]; the model was trained on {self.junction_ids}"
            )

        return code

    def _code_of(self, junction):
        for code, known in enumerate(self.junction_ids):
            if known == junction or str(known) == str(junction):
                return code

        return None

    def _encode_junctions(self, junctions: pd.Series) -> pd.Series:

        if self.junction_ids is None:
            return junctions.astype("category").cat.codes

        codes = {junction: code for code, junction in enumerate(self.junction_ids)}
        unknown = sorted(set(junctions.unique()) - set(codes))

        if unknown:
            raise ValueError(
                f"Unknown junctions {unknown}; the model was trained on {self.junction_ids}"
            )

        return junctions.map(codes)

    def features_for(self, window, target_time, junction_code) -> list:
        """
        One model input row: the readings before `target_time`, the calendar
        features of `target_time` itself, and the junction code.
        """

        target_time = pd.Timestamp(target_time)

        row = list(window)

        # time features (KEEP EXACT ORDER)
        if self.use_hour:
            row.append(target_time.hour)

        if self.use_day_of_week:
            row.append(target_time.dayofweek)

        row.append(target_time.month)
        row.append(target_time.dayofyear)

        # junction LAST
        row.append(junction_code)

        return row

    def transform(self, df: pd.DataFrame):

        df = df.copy()
        df["DateTime"] = pd.to_datetime(df["DateTime"])
        df = df.sort_values(["Junction", "DateTime"])
        df["Junction"] = self._encode_junctions(df["Junction"])

        X, y = [], []

        for junction, group in df.groupby("Junction"):

            group = group.sort_values("DateTime").reset_index(drop=True)
            vehicles = group["Vehicles"].values

            for i in range(self.window_size, len(group)):

                X.append(self.features_for(
                    vehicles[i - self.window_size:i],
                    group.loc[i, "DateTime"],
                    junction,
                ))
                y.append(vehicles[i])

        return np.array(X), np.array(y)
