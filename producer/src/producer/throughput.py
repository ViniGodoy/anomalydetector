"""Throughput monitoring and rate reporting utility."""

import time
from collections.abc import Callable


class RateReporter:
    """Tracks message publishing counts and reports periodic throughput rates."""

    def __init__(
        self,
        interval_seconds: float = 5.0,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        """Initialize RateReporter with reporting interval and clock function.

        Args:
            interval_seconds: Duration between rate reports.
            clock: Time function returning monotonic seconds.
        """
        self._interval_seconds = interval_seconds
        self._clock = clock
        self._last_report_time = clock()
        self._window_count = 0
        self._total_count = 0

    def record(self, count: int = 1) -> None:
        """Record newly published messages.

        Args:
            count: Number of messages published.
        """
        self._window_count += count
        self._total_count += count

    def maybe_report(self) -> str | None:
        """Evaluate whether report interval elapsed and return formatted string if so.

        Returns:
            Formatted rate string or None if interval has not elapsed yet.
        """
        now = self._clock()
        elapsed = now - self._last_report_time
        if elapsed < self._interval_seconds:
            return None

        rate = (self._window_count / elapsed) if elapsed > 0 else 0.0
        report = f"rate={rate:.1f} msg/s (total={self._total_count})"
        self._window_count = 0
        self._last_report_time = now
        return report
