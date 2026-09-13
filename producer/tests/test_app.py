import logging
import random
import threading
from collections.abc import Sequence
from typing import Any

import pytest
from botocore.stub import ANY, Stubber

from producer.app import run_loop
from producer.config import Settings
from producer.generator import DataPointGenerator
from producer.payload import DataPointPayload
from producer.publisher import BatchResult, SnsPublisher
from producer.throughput import RateReporter


def test_run_loop_single_publish_and_stop(
    stubbed_sns: tuple[Any, Stubber],
    caplog: pytest.LogCaptureFixture,
) -> None:
    client, stubber = stubbed_sns
    topic_arn = "arn:aws:sns:us-east-1:000000000000:test-topic"

    settings = Settings(
        topic_arn=topic_arn,
        mean=100.0,
        stddev=10.0,
        interval_seconds=0.0,
        batch_size=1,
        anomaly_probability=1.0,  # Ensure anomaly line
        anomaly_min_sigmas=5.0,
        anomaly_max_sigmas=8.0,
        seed=42,
    )

    stubber.add_response(
        "publish",
        {"MessageId": "msg-anomaly-1"},
        expected_params={
            "TopicArn": topic_arn,
            "Message": ANY,
        },
    )

    publisher = SnsPublisher(client, topic_arn)
    generator = DataPointGenerator(settings, rng=random.Random(42))
    clock_time = [0.0]

    def fake_clock() -> float:
        clock_time[0] += 6.0
        return clock_time[0]

    rate_reporter = RateReporter(interval_seconds=5.0, clock=fake_clock)
    stop_event = threading.Event()

    original_publish = publisher.publish

    def publish_and_stop(payload: DataPointPayload) -> str:
        res = original_publish(payload)
        stop_event.set()
        return res

    publisher.publish = publish_and_stop  # type: ignore[method-assign]

    with caplog.at_level(logging.INFO):
        run_loop(settings, publisher, generator, rate_reporter, stop_event)

    assert "Starting producer loop" in caplog.text
    assert "INJECTED ANOMALY" in caplog.text
    assert "rate=" in caplog.text
    assert "Producer loop terminated cleanly" in caplog.text


def test_run_loop_batch_publish(
    stubbed_sns: tuple[Any, Stubber],
    caplog: pytest.LogCaptureFixture,
) -> None:
    client, stubber = stubbed_sns
    topic_arn = "arn:aws:sns:us-east-1:000000000000:test-topic"

    settings = Settings(
        topic_arn=topic_arn,
        mean=100.0,
        stddev=10.0,
        interval_seconds=0.0,
        batch_size=3,
        anomaly_probability=0.0,  # All normal points
        seed=42,
    )

    stubber.add_response(
        "publish_batch",
        {
            "Successful": [
                {"Id": "0", "MessageId": "msg-0"},
                {"Id": "1", "MessageId": "msg-1"},
                {"Id": "2", "MessageId": "msg-2"},
            ],
            "Failed": [],
        },
        expected_params={
            "TopicArn": topic_arn,
            "PublishBatchRequestEntries": ANY,
        },
    )

    publisher = SnsPublisher(client, topic_arn)
    generator = DataPointGenerator(settings, rng=random.Random(42))
    rate_reporter = RateReporter(interval_seconds=5.0)
    stop_event = threading.Event()

    original_batch = publisher.publish_batch

    def batch_and_stop(payloads: Sequence[DataPointPayload]) -> BatchResult:
        res = original_batch(payloads)
        stop_event.set()
        return res

    publisher.publish_batch = batch_and_stop  # type: ignore[method-assign]

    with caplog.at_level(logging.INFO):
        run_loop(settings, publisher, generator, rate_reporter, stop_event)

    assert "Published batch size=3 anomalies=0" in caplog.text
