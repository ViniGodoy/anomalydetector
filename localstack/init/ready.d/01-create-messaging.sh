#!/bin/bash
set -euo pipefail

echo "==> Configuring LocalStack messaging..."

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
TOPIC="${TOPIC_NAME:-data-points}"
QUEUE="${QUEUE_NAME:-data-points-detector}"
DLQ="${QUEUE}-dlq"

# 1. Create DLQ and get its ARN
awslocal sqs create-queue --queue-name "$DLQ" > /dev/null
DLQ_URL=$(awslocal sqs get-queue-url --queue-name "$DLQ" --query "QueueUrl" --output text)
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query "Attributes.QueueArn" --output text)

# 2. Create main queue with RedrivePolicy pointing to DLQ
REDRIVE=$(printf '{"deadLetterTargetArn":"%s","maxReceiveCount":"3"}' "$DLQ_ARN")
ESCAPED_REDRIVE=$(echo "$REDRIVE" | sed 's/"/\\"/g')
awslocal sqs create-queue \
    --queue-name "$QUEUE" \
    --attributes "{\"RedrivePolicy\":\"$ESCAPED_REDRIVE\",\"VisibilityTimeout\":\"30\"}" > /dev/null

QUEUE_URL=$(awslocal sqs get-queue-url --queue-name "$QUEUE" --query "QueueUrl" --output text)
QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn --query "Attributes.QueueArn" --output text)

# 3. Create SNS topic
TOPIC_ARN=$(awslocal sns create-topic --name "$TOPIC" --query "TopicArn" --output text)

# 4. Subscribe SQS queue to SNS topic with RawMessageDelivery
awslocal sns subscribe \
    --topic-arn "$TOPIC_ARN" \
    --protocol sqs \
    --notification-endpoint "$QUEUE_ARN" \
    --attributes RawMessageDelivery=true > /dev/null

echo "Messaging ready: topic=$TOPIC_ARN queue=$QUEUE_ARN dlq=$DLQ_ARN"
