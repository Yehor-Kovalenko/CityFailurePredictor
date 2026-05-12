from dataclasses import dataclass
from datetime import datetime
from typing import Any


@dataclass
class Request:
    task: str
    data: Any
    timestamp: datetime