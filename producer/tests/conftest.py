import random
from collections.abc import Generator
from typing import Any

import boto3
import pytest
from botocore.stub import Stubber

from producer.config import Settings


@pytest.fixture
def default_settings() -> Settings:
    return Settings(
        topic_arn="arn:aws:sns:us-east-1:000000000000:data-points",
        mean=100.0,
        stddev=10.0,
        interval_seconds=0.1,
        batch_size=1,
        anomaly_probability=0.05,
        anomaly_min_sigmas=5.0,
        anomaly_max_sigmas=8.0,
        seed=42,
    )


@pytest.fixture
def seeded_rng() -> random.Random:
    return random.Random(42)


@pytest.fixture
def stubbed_sns() -> Generator[tuple[Any, Stubber]]:
    client = boto3.client(
        "sns",
        region_name="us-east-1",
        aws_access_key_id="test",
        aws_secret_access_key="test",
    )
    with Stubber(client) as stubber:
        yield client, stubber
