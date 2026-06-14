import pandas as pd
import logging

logger = logging.getLogger(__name__)


class TrafficDataset:

    def __init__(self, path: str):
        self.path = path
        self.df = None

    def load(self):
        logger.info("Loading traffic dataset path=%s", self.path)

        df = pd.read_csv(self.path)

        # parse datetime
        df["DateTime"] = pd.to_datetime(df["DateTime"])

        # sort (VERY important for time series)
        df = df.sort_values(["Junction", "DateTime"])

        self.df = df

        logger.info(
            "Dataset loaded rows=%s junctions=%s",
            len(df),
            df["Junction"].nunique()
        )

        return df

    def get_raw(self):
        return self.df