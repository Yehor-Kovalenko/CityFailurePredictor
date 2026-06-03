from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


@dataclass(frozen=True)
class ElectricityReading:
    event_id: str
    source: str
    household_id: str
    tariff_type: str
    reading_timestamp: datetime
    kwh: float
    ingested_at: datetime

    @staticmethod
    def from_kafka_event(event: dict) -> "ElectricityReading":
        return ElectricityReading(
            event_id=event["eventId"],
            source=event["source"],
            household_id=event["householdId"],
            tariff_type=event["tariffType"],
            reading_timestamp=_parse_datetime(event["readingTimestamp"]),
            kwh=float(event["kwh"]),
            ingested_at=_parse_datetime(event["ingestedAt"])
        )


def _parse_datetime(value: Any) -> datetime:
    if isinstance(value, str):
        return datetime.fromisoformat(value.replace("Z", "+00:00"))

    if isinstance(value, int):
        return datetime.fromtimestamp(value, tz=timezone.utc)

    if isinstance(value, float):
        return datetime.fromtimestamp(value, tz=timezone.utc)

    if isinstance(value, list):
        return _parse_jackson_array_timestamp(value)

    raise ValueError(f"Unsupported datetime value type={type(value)} value={value}")


def _parse_jackson_array_timestamp(value: list) -> datetime:
    year = value[0]
    month = value[1]
    day = value[2]
    hour = value[3] if len(value) > 3 else 0
    minute = value[4] if len(value) > 4 else 0
    second = value[5] if len(value) > 5 else 0
    nanosecond = value[6] if len(value) > 6 else 0

    return datetime(
        year,
        month,
        day,
        hour,
        minute,
        second,
        nanosecond // 1000,
        tzinfo=timezone.utc
    )
