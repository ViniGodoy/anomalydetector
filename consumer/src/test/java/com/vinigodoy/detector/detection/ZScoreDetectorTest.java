package com.vinigodoy.detector.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.vinigodoy.detector.config.DetectorProperties;
import org.junit.jupiter.api.Test;

final class ZScoreDetectorTest {

  @Test
  void invalidMinSamplesThrowsException() {
    assertThatThrownBy(() -> new ZScoreDetector(10, 3.0, 11, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot exceed windowSize");
  }

  @Test
  void constructsFromDetectorProperties() {
    final var props = new DetectorProperties("test-q", 20, 2.5, 5, true);
    final var detector = new ZScoreDetector(props);
    assertThat(detector.currentWindowSize()).isEqualTo(0);
  }

  @Test
  void rejectsNanAndInfinity() {
    final var detector = new ZScoreDetector(10, 3.0, 5, false);

    assertThatThrownBy(() -> detector.evaluate(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> detector.evaluate(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> detector.evaluate(Double.NEGATIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void warmupPhaseProgression() {
    final var minSamples = 4;
    final var detector = new ZScoreDetector(10, 3.0, minSamples, false);

    for (var i = 1; i < minSamples; i++) {
      final var result = detector.evaluate(10.0 * i);
      assertThat(result).isInstanceOf(DetectionResult.Warmup.class);
      final var warmup = (DetectionResult.Warmup) result;
      assertThat(warmup.value()).isEqualTo(10.0 * i);
      assertThat(warmup.samples()).isEqualTo(i);
      assertThat(warmup.required()).isEqualTo(minSamples);
    }

    assertThat(detector.currentWindowSize()).isEqualTo(minSamples - 1);
  }

  @Test
  void scoredPhaseNormalAndAnomaly() {
    // Window size 10, minSamples 4, threshold 3.0
    final var detector = new ZScoreDetector(10, 3.0, 4, false);

    // Warm up with [10, 10, 10] -> 3 samples
    detector.evaluate(10.0);
    detector.evaluate(10.0);
    detector.evaluate(10.0);

    // 4th sample triggers scoring! Baseline is [10, 10, 10], mean=10, stddev=0
    // If we evaluate 10.0 -> s == 0, x == u -> z = 0, OK
    final var r4 = detector.evaluate(10.0);
    assertThat(r4).isInstanceOf(DetectionResult.Scored.class);
    final var scored4 = (DetectionResult.Scored) r4;
    assertThat(scored4.value()).isEqualTo(10.0);
    assertThat(scored4.zScore()).isEqualTo(0.0);
    assertThat(scored4.status()).isEqualTo(Status.OK);

    // Now window has [10, 10, 10, 10], stddev=0
    // Evaluate 50.0 -> s == 0, x != u -> z = +Infinity, ANOMALY
    final var r5 = detector.evaluate(50.0);
    assertThat(r5).isInstanceOf(DetectionResult.Scored.class);
    final var scored5 = (DetectionResult.Scored) r5;
    assertThat(scored5.zScore()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(scored5.status()).isEqualTo(Status.ANOMALY);

    // Because includeAnomaliesInWindow = false, window size should still be 4
    assertThat(detector.currentWindowSize()).isEqualTo(4);
  }

  @Test
  void scoredWithStdDevGreaterThanZero() {
    final var detector = new ZScoreDetector(10, 2.0, 4, false);

    // Provide diverse warmup to give stdDev > 0: [10, 20, 30] -> mean 20, sumSq = 1400, var =
    // 1400/3 - 400 = 66.67
    detector.evaluate(10.0);
    detector.evaluate(20.0);
    detector.evaluate(30.0);

    // 4th point: 20.0 (near mean) -> OK
    final var r1 = detector.evaluate(20.0);
    assertThat(r1).isInstanceOf(DetectionResult.Scored.class);
    final var s1 = (DetectionResult.Scored) r1;
    assertThat(s1.status()).isEqualTo(Status.OK);
    assertThat(s1.zScore()).isLessThanOrEqualTo(2.0);

    // Anomaly point far away: 200.0
    final var r2 = detector.evaluate(200.0);
    assertThat(r2).isInstanceOf(DetectionResult.Scored.class);
    final var s2 = (DetectionResult.Scored) r2;
    assertThat(s2.status()).isEqualTo(Status.ANOMALY);
    assertThat(s2.zScore()).isGreaterThan(2.0);
  }

  @Test
  void anomalyIncludedWhenConfigured() {
    final var detector = new ZScoreDetector(10, 2.0, 3, true);

    detector.evaluate(10.0);
    detector.evaluate(10.0);
    assertThat(detector.currentWindowSize()).isEqualTo(2);

    // Anomaly evaluation with includeAnomaliesInWindow = true
    final var res = detector.evaluate(100.0);
    assertThat(res).isInstanceOf(DetectionResult.Scored.class);
    final var scored = (DetectionResult.Scored) res;
    assertThat(scored.status()).isEqualTo(Status.ANOMALY);

    // Anomaly admitted into window
    assertThat(detector.currentWindowSize()).isEqualTo(3);
  }

  @Test
  void boundaryConditionZScoreEqualsThresholdIsOk() {
    // Window has [0, 4] -> mean 2.0, stddev 2.0
    // If x = 6.0 -> z = |6 - 2| / 2 = 2.0.
    // With threshold = 2.0, z > 2.0 is FALSE -> status OK!
    final var detector = new ZScoreDetector(10, 2.0, 3, false);
    detector.evaluate(0.0); // warmup 1/3
    detector.evaluate(4.0); // warmup 2/3

    // Now window has [0.0, 4.0], size=2 (minSamples=3).
    // Evaluate x=6.0: scored against [0, 4]
    final var result = detector.evaluate(6.0);
    assertThat(result).isInstanceOf(DetectionResult.Scored.class);
    final var scored = (DetectionResult.Scored) result;
    assertThat(scored.zScore()).isCloseTo(2.0, within(1e-9));
    assertThat(scored.status()).isEqualTo(Status.OK);
  }

  @Test
  void statusEnumCoverage() {
    assertThat(Status.valueOf("OK")).isEqualTo(Status.OK);
    assertThat(Status.valueOf("ANOMALY")).isEqualTo(Status.ANOMALY);
    assertThat(Status.values()).containsExactly(Status.OK, Status.ANOMALY);
  }
}
