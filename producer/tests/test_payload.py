import json
import uuid
from datetime import UTC, datetime

from producer.payload import DataPoint, serialize


def test_payload_contract_keys() -> None:
    point_id = uuid.uuid4()
    ts = datetime(2026, 9, 13, 12, 0, 0, tzinfo=UTC)
    dp = DataPoint(value=101.23456, point_id=point_id, timestamp=ts)
    payload = dp.to_payload()

    # Exact contract keys (camelCase)
    assert set(payload.keys()) == {"id", "timestamp", "value"}
    assert payload["id"] == str(point_id)
    assert payload["timestamp"] == "2026-09-13T12:00:00+00:00"
    assert payload["value"] == 101.2346


def test_payload_serialization_roundtrip() -> None:
    dp = DataPoint(value=42.1)
    payload = dp.to_payload()
    json_str = serialize(payload)

    # Compact JSON without spaces around delimiters
    assert ", " not in json_str
    assert ": " not in json_str

    deserialized = json.loads(json_str)
    assert deserialized == payload
