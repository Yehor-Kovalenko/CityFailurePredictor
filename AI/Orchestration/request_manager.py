from dataclasses import dataclass
from datetime import datetime
from typing import Any


from dataclasses import dataclass
from typing import Any, Optional
from datetime import datetime

@dataclass
class Request:
    task: str
    timestamp: datetime
    data: Any
    params: Optional[dict] = None
