import random
import statistics

from producer.config import Settings
from producer.generator import DataPointGenerator


def test_generator_deterministic_seeding() -> None:
    settings = Settings(
        topic_arn="arn:test",
        mean=100.0,
        stddev=10.0,
        seed=12345,
    )
    gen1 = DataPointGenerator(settings, rng=random.Random(12345))
    gen2 = DataPointGenerator(settings, rng=random.Random(12345))

    points1 = [gen1.next_point() for _ in range(50)]
    points2 = [gen2.next_point() for _ in range(50)]

    assert [p.value for p in points1] == [p.value for p in points2]
    assert [p.is_anomaly for p in points1] == [p.is_anomaly for p in points2]
    assert [p.sigmas for p in points1] == [p.sigmas for p in points2]


def test_generator_no_anomalies_distribution() -> None:
    settings = Settings(
        topic_arn="arn:test",
        mean=100.0,
        stddev=10.0,
        anomaly_probability=0.0,
        seed=42,
    )
    gen = DataPointGenerator(settings, rng=random.Random(42))
    points = [gen.next_point() for _ in range(10_000)]

    assert all(not p.is_anomaly for p in points)

    values = [p.value for p in points]
    sample_mean = statistics.fmean(values)
    sample_stddev = statistics.pstdev(values)

    # Within 3% of configured parameters for 10k samples
    assert abs(sample_mean - 100.0) < 0.5
    assert abs(sample_stddev - 10.0) < 0.5


def test_generator_all_anomalies() -> None:
    settings = Settings(
        topic_arn="arn:test",
        mean=100.0,
        stddev=10.0,
        anomaly_probability=1.0,
        anomaly_min_sigmas=5.0,
        anomaly_max_sigmas=8.0,
        seed=42,
    )
    gen = DataPointGenerator(settings, rng=random.Random(42))
    points = [gen.next_point() for _ in range(200)]

    assert all(p.is_anomaly for p in points)
    for p in points:
        sigmas_mag = abs(p.sigmas)
        assert 5.0 <= sigmas_mag <= 8.0
        expected_val = settings.mean + (p.sigmas * settings.stddev)
        assert abs(p.value - expected_val) < 1e-9

    has_positive = any(p.sigmas > 0 for p in points)
    has_negative = any(p.sigmas < 0 for p in points)
    assert has_positive
    assert has_negative


def test_generator_mixed_frequency() -> None:
    settings = Settings(
        topic_arn="arn:test",
        mean=100.0,
        stddev=10.0,
        anomaly_probability=0.20,
        seed=999,
    )
    gen = DataPointGenerator(settings, rng=random.Random(999))
    points = [gen.next_point() for _ in range(5_000)]

    anomaly_count = sum(1 for p in points if p.is_anomaly)
    observed_p = anomaly_count / 5_000

    # Within loose tolerance
    assert 0.17 <= observed_p <= 0.23


def test_generator_iterator_protocol() -> None:
    settings = Settings(topic_arn="arn:test", seed=1)
    gen = DataPointGenerator(settings, rng=random.Random(1))
    it = iter(gen)
    p1 = next(it)
    p2 = next(it)
    assert isinstance(p1.value, float)
    assert isinstance(p2.value, float)
