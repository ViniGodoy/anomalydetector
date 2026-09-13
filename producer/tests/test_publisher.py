from typing import Any

import pytest
from botocore.exceptions import ClientError
from botocore.stub import ANY, Stubber

from producer.payload import DataPoint
from producer.publisher import SnsPublisher


def test_publisher_single(stubbed_sns: tuple[Any, Stubber]) -> None:
    client, stubber = stubbed_sns
    topic_arn = "arn:aws:sns:us-east-1:000000000000:test-topic"
    publisher = SnsPublisher(client, topic_arn)

    dp = DataPoint(value=99.5)
    payload = dp.to_payload()

    stubber.add_response(
        "publish",
        {"MessageId": "msg-abc-123"},
        expected_params={
            "TopicArn": topic_arn,
            "Message": ANY,
        },
    )

    msg_id = publisher.publish(payload)
    assert msg_id == "msg-abc-123"


def test_publisher_batch_success(stubbed_sns: tuple[Any, Stubber]) -> None:
    client, stubber = stubbed_sns
    topic_arn = "arn:aws:sns:us-east-1:000000000000:test-topic"
    publisher = SnsPublisher(client, topic_arn)

    payloads = [DataPoint(value=float(i)).to_payload() for i in range(5)]

    stubber.add_response(
        "publish_batch",
        {
            "Successful": [{"Id": str(i), "MessageId": f"msg-{i}"} for i in range(5)],
            "Failed": [],
        },
        expected_params={
            "TopicArn": topic_arn,
            "PublishBatchRequestEntries": [{"Id": str(i), "Message": ANY} for i in range(5)],
        },
    )

    result = publisher.publish_batch(payloads)
    assert result.successful == 5
    assert len(result.failed) == 0


def test_publisher_batch_partial_failure(stubbed_sns: tuple[Any, Stubber]) -> None:
    client, stubber = stubbed_sns
    topic_arn = "arn:aws:sns:us-east-1:000000000000:test-topic"
    publisher = SnsPublisher(client, topic_arn)

    payloads = [DataPoint(value=1.0).to_payload(), DataPoint(value=2.0).to_payload()]

    stubber.add_response(
        "publish_batch",
        {
            "Successful": [{"Id": "0", "MessageId": "msg-0"}],
            "Failed": [
                {
                    "Id": "1",
                    "Code": "InternalError",
                    "Message": "Server error",
                    "SenderFault": False,
                }
            ],
        },
        expected_params={
            "TopicArn": topic_arn,
            "PublishBatchRequestEntries": ANY,
        },
    )

    result = publisher.publish_batch(payloads)
    assert result.successful == 1
    assert len(result.failed) == 1
    assert "InternalError" in result.failed[0]


def test_publisher_batch_exceeds_max(stubbed_sns: tuple[Any, Stubber]) -> None:
    client, _ = stubbed_sns
    publisher = SnsPublisher(client, "arn:test")
    payloads = [DataPoint(value=float(i)).to_payload() for i in range(11)]

    with pytest.raises(ValueError, match="publish_batch supports up to 10 entries"):
        publisher.publish_batch(payloads)


def test_publisher_empty_batch(stubbed_sns: tuple[Any, Stubber]) -> None:
    client, _ = stubbed_sns
    publisher = SnsPublisher(client, "arn:test")
    result = publisher.publish_batch([])
    assert result.successful == 0
    assert result.failed == ()


def test_publisher_client_error(stubbed_sns: tuple[Any, Stubber]) -> None:
    client, stubber = stubbed_sns
    publisher = SnsPublisher(client, "arn:test")
    payload = DataPoint(value=1.0).to_payload()

    stubber.add_client_error(
        "publish",
        service_error_code="NotFound",
        service_message="Topic does not exist",
    )

    with pytest.raises(ClientError):
        publisher.publish(payload)
