import logging
import os
import random
import signal
import sys
import threading
from collections.abc import Sequence
from datetime import UTC, datetime
from types import FrameType
from typing import TYPE_CHECKING

import boto3
from botocore.exceptions import BotoCoreError, ClientError

from producer.config import Settings
from producer.generator import DataPointGenerator, GeneratedPoint
from producer.payload import DataPoint, DataPointPayload
from producer.publisher import SnsPublisher
from producer.throughput import RateReporter

if TYPE_CHECKING:
    from types_boto3_sns.client import SNSClient

logger = logging.getLogger("producer")


class UtcFormatter(logging.Formatter):
    """Logging formatter producing ISO-8601 UTC timestamps."""

    def formatTime(self, record: logging.LogRecord, datefmt: str | None = None) -> str:  # noqa: N802
        """Format timestamp in UTC ISO-8601 format."""
        dt = datetime.fromtimestamp(record.created, tz=UTC)
        if datefmt:
            return dt.strftime(datefmt)
        return dt.isoformat(timespec="milliseconds")


def setup_logging() -> None:
    """Configure structured console logging with UTC timestamps."""
    handler = logging.StreamHandler(sys.stdout)
    formatter = UtcFormatter(
        fmt="[%(asctime)s] %(levelname)s [%(name)s] %(message)s",
    )
    handler.setFormatter(formatter)
    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.handlers.clear()
    root.addHandler(handler)


def _publish_single(
    publisher: SnsPublisher,
    point: GeneratedPoint,
    rate_reporter: RateReporter,
) -> None:
    """Publish a single data point and log outcome."""
    dp = DataPoint(value=point.value)
    payload = dp.to_payload()
    publisher.publish(payload)
    rate_reporter.record(1)

    if point.is_anomaly:
        logger.warning(
            "Published id=%s value=%.2f | INJECTED ANOMALY (%+.1f sigma)",
            payload["id"],
            payload["value"],
            point.sigmas,
        )
    else:
        logger.info(
            "Published id=%s value=%.2f",
            payload["id"],
            payload["value"],
        )


def _publish_batch(
    publisher: SnsPublisher,
    points: Sequence[GeneratedPoint],
    rate_reporter: RateReporter,
) -> None:
    """Publish a batch of data points and log outcome."""
    items: list[tuple[GeneratedPoint, DataPointPayload]] = [
        (p, DataPoint(value=p.value).to_payload()) for p in points
    ]
    payloads = [item[1] for item in items]
    result = publisher.publish_batch(payloads)
    rate_reporter.record(result.successful)

    anomalies_count = 0
    for p, payload in items:
        if p.is_anomaly:
            anomalies_count += 1
            logger.warning(
                "Published id=%s value=%.2f | INJECTED ANOMALY (%+.1f sigma)",
                payload["id"],
                payload["value"],
                p.sigmas,
            )

    logger.info(
        "Published batch size=%d anomalies=%d (successful=%d, failed=%d)",
        len(points),
        anomalies_count,
        result.successful,
        len(result.failed),
    )
    if result.failed:
        for failure in result.failed:
            logger.error("Batch entry failure: %s", failure)


def run_loop(
    settings: Settings,
    publisher: SnsPublisher,
    generator: DataPointGenerator,
    rate_reporter: RateReporter,
    stop_event: threading.Event,
) -> None:
    """Run continuous publishing loop until stop_event is triggered.

    Args:
        settings: Runtime configuration.
        publisher: SNS publisher instance.
        generator: Data point generator.
        rate_reporter: Throughput monitor.
        stop_event: Event signaling loop termination.
    """
    logger.info(
        "Starting producer loop: topic=%s, mean=%.1f, stddev=%.1f, batch_size=%d, interval=%.3fs",
        settings.topic_arn,
        settings.mean,
        settings.stddev,
        settings.batch_size,
        settings.interval_seconds,
    )

    while not stop_event.is_set():
        try:
            if settings.batch_size == 1:
                point = generator.next_point()
                _publish_single(publisher, point, rate_reporter)
            else:
                batch_points = [generator.next_point() for _ in range(settings.batch_size)]
                _publish_batch(publisher, batch_points, rate_reporter)

            report = rate_reporter.maybe_report()
            if report:
                logger.info(report)

        except (BotoCoreError, ClientError):
            logger.exception("AWS communication error during publishing")
            # Prevent hot spinning on connection error
            backoff = max(0.1, settings.interval_seconds)
            if stop_event.wait(backoff):
                break
            continue

        if settings.interval_seconds > 0.0:
            stop_event.wait(settings.interval_seconds)

    logger.info("Producer loop terminated cleanly.")


def main() -> int:
    """Entry point for the producer service."""
    setup_logging()

    try:
        settings = Settings.from_env(os.environ)
    except ValueError:
        logger.exception("Configuration error")
        return 1

    endpoint_url = os.environ.get("AWS_ENDPOINT_URL")
    region_name = os.environ.get("AWS_DEFAULT_REGION", "us-east-1")
    aws_access_key_id = os.environ.get("AWS_ACCESS_KEY_ID", "test")
    aws_secret_access_key = os.environ.get("AWS_SECRET_ACCESS_KEY", "test")

    client: SNSClient = boto3.client(
        "sns",
        endpoint_url=endpoint_url,
        region_name=region_name,
        aws_access_key_id=aws_access_key_id,
        aws_secret_access_key=aws_secret_access_key,
    )

    # S311: Standard random used for simulation data, non-cryptographic
    rng = random.Random(settings.seed)  # noqa: S311
    generator = DataPointGenerator(settings, rng=rng)
    publisher = SnsPublisher(client, settings.topic_arn)
    rate_reporter = RateReporter(interval_seconds=5.0)

    stop_event = threading.Event()

    def handle_signal(signum: int, _frame: FrameType | None) -> None:
        signame = signal.Signals(signum).name
        logger.info("Received termination signal %s, initiating graceful shutdown...", signame)
        stop_event.set()

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    run_loop(settings, publisher, generator, rate_reporter, stop_event)
    return 0
