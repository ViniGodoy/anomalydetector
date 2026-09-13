import random
from collections.abc import Iterator
from dataclasses import dataclass
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from producer.config import Settings


@dataclass(frozen=True, slots=True)
class GeneratedPoint:
    """Single generated data point observation."""

    value: float
    is_anomaly: bool
    sigmas: float


class DataPointGenerator:
    """Generates continuous stream of Gaussian data points with injected outliers."""

    def __init__(self, settings: "Settings", rng: random.Random | None = None) -> None:
        """Initialize generator with settings and optional random instance.

        Args:
            settings: Runtime settings.
            rng: Random number generator instance (defaults to new random.Random(seed)).
        """
        self._settings = settings
        # S311: Standard random is used for simulation data generation,
        # not security-sensitive crypto.
        self._rng = rng if rng is not None else random.Random(settings.seed)  # noqa: S311

    def next_point(self) -> GeneratedPoint:
        """Generate the next data point.

        Returns:
            GeneratedPoint containing value, anomaly flag, and signed sigmas from mean.
        """
        # S311: Simulation RNG check
        if self._rng.random() < self._settings.anomaly_probability:
            direction = 1.0 if self._rng.random() < 0.5 else -1.0
            sigma_mag = self._rng.uniform(
                self._settings.anomaly_min_sigmas,
                self._settings.anomaly_max_sigmas,
            )
            signed_sigmas = direction * sigma_mag
            value = self._settings.mean + (signed_sigmas * self._settings.stddev)
            return GeneratedPoint(value=value, is_anomaly=True, sigmas=signed_sigmas)

        value = self._rng.gauss(self._settings.mean, self._settings.stddev)
        sigmas = (value - self._settings.mean) / self._settings.stddev
        return GeneratedPoint(value=value, is_anomaly=False, sigmas=sigmas)

    def __iter__(self) -> Iterator[GeneratedPoint]:
        """Return an infinite iterator yielding points.

        Yields:
            GeneratedPoint instances.
        """
        while True:
            yield self.next_point()
