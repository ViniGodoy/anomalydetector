package com.vinigodoy.detector.detection;

/**
 * Sealed hierarchy representing the outcome of evaluating a data point.
 *
 * <p>Permits {@link Warmup} during the initial baseline collection phase and {@link Scored} once
 * sufficient samples are available for Z-score calculation.
 */
public sealed interface DetectionResult permits DetectionResult.Warmup, DetectionResult.Scored {

  double value();

  /**
   * Result produced when the window does not yet contain enough samples to score points.
   *
   * @param value the data point value
   * @param samples current count of samples collected in the window
   * @param required minimum samples required before scoring begins
   */
  record Warmup(double value, int samples, int required) implements DetectionResult {}

  /**
   * Result produced when a data point is scored against the rolling baseline.
   *
   * @param value the data point value
   * @param zScore computed Z-score deviation from the baseline mean
   * @param status classification status (OK or ANOMALY)
   */
  record Scored(double value, double zScore, Status status) implements DetectionResult {}
}
