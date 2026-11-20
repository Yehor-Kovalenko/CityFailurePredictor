from dataclasses import dataclass
from datetime import datetime

from AI.Domain.Datasets.electricity_reading import _parse_datetime


@dataclass(frozen=True)
class TrafficReading:
    event_id: str
    source: str
    junction_id: str
    reading_timestamp: datetime
    vehicles: float
    ingested_at: datetime

    @staticmethod
    def from_kafka_event(event: dict) -> "TrafficReading":
        return TrafficReading(
            event_id=event["eventId"],
            source=event["source"],
            junction_id=str(event["junctionId"]),
            reading_timestamp=_parse_datetime(event["readingTimestamp"]),
            vehicles=float(event["vehicles"]),
            ingested_at=_parse_datetime(event["ingestedAt"])
        )
