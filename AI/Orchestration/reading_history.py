import logging
import math
from collections import deque
from datetime import datetime, timedelta

logger = logging.getLogger(__name__)


class ReadingHistory:
    """
    Keeps the last `window_size` readings of every household on a regular time
    grid, which is what the forecasting model was trained on.

    - a repeated timestamp (redelivery, retry) does not add a second value,
    - a reading older than the newest one is ignored,
    - a short gap is filled by linear interpolation,
    - a longer gap starts the history over.
    """

    def __init__(self, window_size: int, step: timedelta = timedelta(minutes=30), max_gap_steps: int = 4):
        self.window_size = window_size
        self.step = step
        self.max_gap_steps = max_gap_steps

        self._values: dict[str, deque] = {}
        self._last_timestamp: dict[str, datetime] = {}

    def add(self, household_id: str, timestamp: datetime, kwh: float) -> list[float] | None:
        """Returns the model input window, or None when no prediction should be made."""

        if not math.isfinite(kwh):
            logger.warning("Ignoring non-finite reading household_id=%s kwh=%s", household_id, kwh)
            return None

        values = self._values.setdefault(household_id, deque(maxlen=self.window_size))
        last_timestamp = self._last_timestamp.get(household_id)

        if last_timestamp is not None:
            if timestamp < last_timestamp:
                logger.warning(
                    "Ignoring out-of-order reading household_id=%s timestamp=%s newest=%s",
                    household_id, timestamp, last_timestamp,
                )
                return None

            if timestamp == last_timestamp:
                return self._window(values)

            missing = max(0, round((timestamp - last_timestamp) / self.step) - 1)

            if missing > self.max_gap_steps:
                logger.info(
                    "Gap of %s readings for household_id=%s; starting history over",
                    missing, household_id,
                )
                values.clear()
            elif missing:
                previous = values[-1]
                for i in range(1, missing + 1):
                    values.append(previous + (kwh - previous) * i / (missing + 1))

        values.append(kwh)
        self._last_timestamp[household_id] = timestamp

        return self._window(values)

    def size(self, household_id: str) -> int:
        return len(self._values.get(household_id, ()))

    def _window(self, values: deque) -> list[float] | None:
        if len(values) < self.window_size:
            return None

        return list(values)
