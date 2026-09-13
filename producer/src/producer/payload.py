import json
import uuid
from datetime import UTC, datetime
from typing import TypedDict


class DataPointPayload(TypedDict):
    """Wire contract for data point messages sent to SNS/SQS.

    All wire keys follow camelCase.
    """

    id: str
    timestamp: str
    value: float


class DataPoint:
    """Internal domain model for a data point."""

    __slots__ = ("id", "timestamp", "value")

    def __init__(
        self,
        value: float,
        point_id: uuid.UUID | None = None,
        timestamp: datetime | None = None,
    ) -> None:
        """Initialize data point domain entity.

        Args:
            value: Numerical reading value.
            point_id: Unique identifier (defaults to uuid4).
            timestamp: Time of observation (defaults to current UTC time).
        """
        self.id: uuid.UUID = point_id if point_id is not None else uuid.uuid4()
        self.timestamp: datetime = timestamp if timestamp is not None else datetime.now(UTC)
        self.value: float = value

    def to_payload(self) -> DataPointPayload:
        """Map internal entity to external camelCase wire dictionary.

        Returns:
            DataPointPayload dictionary.
        """
        return {
            "id": str(self.id),
            "timestamp": self.timestamp.isoformat(),
            "value": round(self.value, 4),
        }


def serialize(payload: DataPointPayload) -> str:
    """Serialize a payload dictionary to compact JSON string.

    Args:
        payload: Wire dictionary.

    Returns:
        Compact JSON string without unnecessary whitespace.
    """
    return json.dumps(payload, separators=(",", ":"))
