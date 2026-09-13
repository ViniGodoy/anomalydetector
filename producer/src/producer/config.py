from collections.abc import Mapping
from dataclasses import dataclass
from typing import Self


def _parse_float(env: Mapping[str, str], key: str, default: float) -> float:
    """Parse a float environment variable.

    Args:
        env: Environment variable mapping.
        key: Variable name.
        default: Default value if not set.

    Returns:
        Parsed float value.

    Raises:
        ValueError: If value cannot be parsed as a float.
    """
    raw = env.get(key)
    if raw is None or raw.strip() == "":
        return default
    try:
        return float(raw)
    except ValueError as err:
        msg = f"Environment variable {key} must be a valid float, got: {raw!r}"
        raise ValueError(msg) from err


def _parse_int(env: Mapping[str, str], key: str, default: int) -> int:
    """Parse an integer environment variable.

    Args:
        env: Environment variable mapping.
        key: Variable name.
        default: Default value if not set.

    Returns:
        Parsed integer value.

    Raises:
        ValueError: If value cannot be parsed as an integer.
    """
    raw = env.get(key)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError as err:
        msg = f"Environment variable {key} must be a valid integer, got: {raw!r}"
        raise ValueError(msg) from err


def _parse_optional_int(env: Mapping[str, str], key: str) -> int | None:
    """Parse an optional integer environment variable.

    Args:
        env: Environment variable mapping.
        key: Variable name.

    Returns:
        Parsed integer or None if not set.

    Raises:
        ValueError: If value is present but cannot be parsed as an integer.
    """
    raw = env.get(key)
    if raw is None or raw.strip() == "":
        return None
    try:
        return int(raw)
    except ValueError as err:
        msg = f"Environment variable {key} must be a valid integer, got: {raw!r}"
        raise ValueError(msg) from err


@dataclass(frozen=True, slots=True, kw_only=True)
class Settings:
    """Producer runtime settings loaded from environment variables."""

    topic_arn: str
    mean: float = 100.0
    stddev: float = 10.0
    interval_seconds: float = 0.25
    batch_size: int = 1
    anomaly_probability: float = 0.02
    anomaly_min_sigmas: float = 5.0
    anomaly_max_sigmas: float = 8.0
    seed: int | None = None

    def __post_init__(self) -> None:
        """Validate settings invariants."""
        if not self.topic_arn.strip():
            msg = "SNS_TOPIC_ARN must not be empty"
            raise ValueError(msg)
        if self.stddev <= 0.0:
            msg = f"PRODUCER_STDDEV must be positive, got: {self.stddev}"
            raise ValueError(msg)
        if self.interval_seconds < 0.0:
            msg = f"PRODUCER_INTERVAL_SECONDS must be non-negative, got: {self.interval_seconds}"
            raise ValueError(msg)
        if not (1 <= self.batch_size <= 10):
            msg = f"PRODUCER_BATCH_SIZE must be between 1 and 10, got: {self.batch_size}"
            raise ValueError(msg)
        if not (0.0 <= self.anomaly_probability <= 1.0):
            msg = (
                "PRODUCER_ANOMALY_PROBABILITY must be between 0.0 and 1.0, "
                f"got: {self.anomaly_probability}"
            )
            raise ValueError(msg)
        if self.anomaly_min_sigmas <= 1.0:
            msg = (
                f"PRODUCER_ANOMALY_MIN_SIGMAS must be greater than 1.0, got: "
                f"{self.anomaly_min_sigmas}"
            )
            raise ValueError(msg)
        if self.anomaly_min_sigmas > self.anomaly_max_sigmas:
            msg = (
                f"PRODUCER_ANOMALY_MIN_SIGMAS ({self.anomaly_min_sigmas}) must be <= "
                f"PRODUCER_ANOMALY_MAX_SIGMAS ({self.anomaly_max_sigmas})"
            )
            raise ValueError(msg)

    @classmethod
    def from_env(cls, env: Mapping[str, str]) -> Self:
        """Load and validate settings from an environment mapping.

        Args:
            env: Environment mapping (e.g. os.environ).

        Returns:
            Validated Settings instance.

        Raises:
            ValueError: If required variables are missing or invalid.
        """
        topic_arn = env.get("SNS_TOPIC_ARN")
        if topic_arn is None or topic_arn.strip() == "":
            msg = "Environment variable SNS_TOPIC_ARN is required"
            raise ValueError(msg)

        return cls(
            topic_arn=topic_arn.strip(),
            mean=_parse_float(env, "PRODUCER_MEAN", 100.0),
            stddev=_parse_float(env, "PRODUCER_STDDEV", 10.0),
            interval_seconds=_parse_float(env, "PRODUCER_INTERVAL_SECONDS", 0.25),
            batch_size=_parse_int(env, "PRODUCER_BATCH_SIZE", 1),
            anomaly_probability=_parse_float(env, "PRODUCER_ANOMALY_PROBABILITY", 0.02),
            anomaly_min_sigmas=_parse_float(env, "PRODUCER_ANOMALY_MIN_SIGMAS", 5.0),
            anomaly_max_sigmas=_parse_float(env, "PRODUCER_ANOMALY_MAX_SIGMAS", 8.0),
            seed=_parse_optional_int(env, "PRODUCER_SEED"),
        )
