from collections.abc import Sequence
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

from producer.payload import DataPointPayload, serialize

if TYPE_CHECKING:
    from types_boto3_sns.client import SNSClient
    from types_boto3_sns.type_defs import PublishBatchRequestEntryTypeDef
else:
    SNSClient = Any
    PublishBatchRequestEntryTypeDef = Any


@dataclass(frozen=True, slots=True)
class BatchResult:
    """Outcome of an SNS publish_batch operation."""

    successful: int
    failed: tuple[str, ...]


class SnsPublisher:
    """Publishes serialized data points to an Amazon SNS topic."""

    def __init__(self, client: SNSClient, topic_arn: str) -> None:
        """Initialize publisher with SNS client and destination topic ARN.

        Args:
            client: Boto3 SNS client.
            topic_arn: Target SNS topic ARN.
        """
        self._client = client
        self._topic_arn = topic_arn

    def publish(self, payload: DataPointPayload) -> str:
        """Publish a single data point to the SNS topic.

        Args:
            payload: Wire payload dictionary.

        Returns:
            SNS Message ID assigned by AWS.
        """
        message_body = serialize(payload)
        response = self._client.publish(
            TopicArn=self._topic_arn,
            Message=message_body,
        )
        message_id: str = response["MessageId"]
        return message_id

    def publish_batch(self, payloads: Sequence[DataPointPayload]) -> BatchResult:
        """Publish a batch of data points (up to 10) to the SNS topic.

        Args:
            payloads: Sequence of wire payload dictionaries.

        Returns:
            BatchResult with counts of successful and failed items.

        Raises:
            ValueError: If payloads sequence exceeds 10 items.
        """
        if len(payloads) > 10:
            msg = f"publish_batch supports up to 10 entries, got {len(payloads)}"
            raise ValueError(msg)

        if not payloads:
            return BatchResult(successful=0, failed=())

        entries: list[PublishBatchRequestEntryTypeDef] = [
            {"Id": str(i), "Message": serialize(p)} for i, p in enumerate(payloads)
        ]

        response = self._client.publish_batch(
            TopicArn=self._topic_arn,
            PublishBatchRequestEntries=entries,
        )

        successful_count = len(response.get("Successful", []))
        failed_entries = response.get("Failed", [])
        failed_messages = tuple(
            f"id={f.get('Id')}: {f.get('Code', 'UnknownCode')} - {f.get('Message', 'UnknownError')}"
            for f in failed_entries
        )

        return BatchResult(successful=successful_count, failed=failed_messages)
