import pytest

from producer.config import Settings


def test_settings_defaults() -> None:
    env = {"SNS_TOPIC_ARN": "arn:aws:sns:us-east-1:000000000000:test-topic"}
    settings = Settings.from_env(env)
    assert settings.topic_arn == "arn:aws:sns:us-east-1:000000000000:test-topic"
    assert settings.mean == 100.0
    assert settings.stddev == 10.0
    assert settings.interval_seconds == 0.25
    assert settings.batch_size == 1
    assert settings.anomaly_probability == 0.02
    assert settings.anomaly_min_sigmas == 5.0
    assert settings.anomaly_max_sigmas == 8.0
    assert settings.seed is None


def test_settings_overrides() -> None:
    env = {
        "SNS_TOPIC_ARN": "arn:aws:sns:us-east-1:000000000000:test-topic",
        "PRODUCER_MEAN": "50.5",
        "PRODUCER_STDDEV": "2.5",
        "PRODUCER_INTERVAL_SECONDS": "0",
        "PRODUCER_BATCH_SIZE": "10",
        "PRODUCER_ANOMALY_PROBABILITY": "0.1",
        "PRODUCER_ANOMALY_MIN_SIGMAS": "3.0",
        "PRODUCER_ANOMALY_MAX_SIGMAS": "6.0",
        "PRODUCER_SEED": "1234",
    }
    settings = Settings.from_env(env)
    assert settings.mean == 50.5
    assert settings.stddev == 2.5
    assert settings.interval_seconds == 0.0
    assert settings.batch_size == 10
    assert settings.anomaly_probability == 0.1
    assert settings.anomaly_min_sigmas == 3.0
    assert settings.anomaly_max_sigmas == 6.0
    assert settings.seed == 1234


def test_missing_topic_arn() -> None:
    with pytest.raises(ValueError, match="SNS_TOPIC_ARN"):
        Settings.from_env({})


@pytest.mark.parametrize(
    ("field", "val", "match"),
    [
        ("PRODUCER_STDDEV", "0", "PRODUCER_STDDEV"),
        ("PRODUCER_STDDEV", "-1.0", "PRODUCER_STDDEV"),
        ("PRODUCER_INTERVAL_SECONDS", "-0.1", "PRODUCER_INTERVAL_SECONDS"),
        ("PRODUCER_BATCH_SIZE", "0", "PRODUCER_BATCH_SIZE"),
        ("PRODUCER_BATCH_SIZE", "11", "PRODUCER_BATCH_SIZE"),
        ("PRODUCER_ANOMALY_PROBABILITY", "-0.01", "PRODUCER_ANOMALY_PROBABILITY"),
        ("PRODUCER_ANOMALY_PROBABILITY", "1.05", "PRODUCER_ANOMALY_PROBABILITY"),
        ("PRODUCER_ANOMALY_MIN_SIGMAS", "1.0", "PRODUCER_ANOMALY_MIN_SIGMAS"),
        ("PRODUCER_ANOMALY_MIN_SIGMAS", "9.0", "PRODUCER_ANOMALY_MIN_SIGMAS"),
    ],
)
def test_invalid_settings_boundaries(field: str, val: str, match: str) -> None:
    env = {
        "SNS_TOPIC_ARN": "arn:aws:sns:us-east-1:000000000000:test-topic",
        field: val,
    }
    with pytest.raises(ValueError, match=match):
        Settings.from_env(env)


def test_invalid_types_in_env() -> None:
    with pytest.raises(ValueError, match="PRODUCER_MEAN"):
        Settings.from_env({"SNS_TOPIC_ARN": "arn:test", "PRODUCER_MEAN": "not-a-number"})

    with pytest.raises(ValueError, match="PRODUCER_BATCH_SIZE"):
        Settings.from_env({"SNS_TOPIC_ARN": "arn:test", "PRODUCER_BATCH_SIZE": "one"})

    with pytest.raises(ValueError, match="PRODUCER_SEED"):
        Settings.from_env({"SNS_TOPIC_ARN": "arn:test", "PRODUCER_SEED": "invalid-seed"})
