package com.vinigodoy.detector.detection;

import com.vinigodoy.detector.config.DetectorProperties;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Thread-safe real-time Z-score anomaly detector backed by an O(1) {@link RollingWindow}.
 *
 * <p>Employs an explicit {@link ReentrantLock} instead of {@code synchronized} to prevent carrier
 * thread pinning when executed on Java 21 virtual threads.
 */
@Component
public final class ZScoreDetector {

  private final RollingWindow window;
  private final double zThreshold;
  private final int minSamples;
  private final boolean includeAnomaliesInWindow;
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Construct a detector using Spring Boot configuration properties.
   *
   * @param properties validated detector properties
   */
  @Autowired
  public ZScoreDetector(DetectorProperties properties) {
    this(
        properties.windowSize(),
        properties.zThreshold(),
        properties.minSamples(),
        properties.includeAnomaliesInWindow());
  }

  /**
   * Construct a detector with explicit parameters.
   *
   * @param windowSize capacity of the rolling window
   * @param zThreshold threshold number of standard deviations for anomaly classification
   * @param minSamples minimum warm-up samples required before scoring
   * @param includeAnomaliesInWindow whether detected anomalies are admitted into the baseline
   */
  public ZScoreDetector(
      int windowSize, double zThreshold, int minSamples, boolean includeAnomaliesInWindow) {
    if (minSamples > windowSize) {
      throw new IllegalArgumentException(
          "minSamples (" + minSamples + ") cannot exceed windowSize (" + windowSize + ")");
    }
    this.window = new RollingWindow(windowSize);
    this.zThreshold = zThreshold;
    this.minSamples = minSamples;
    this.includeAnomaliesInWindow = includeAnomaliesInWindow;
  }

  /**
   * Evaluate a numerical data point against the rolling baseline.
   *
   * @param x data point value
   * @return {@link DetectionResult.Warmup} during warmup, or {@link DetectionResult.Scored} once
   *     sufficient samples exist
   * @throws IllegalArgumentException if x is NaN or Infinite
   */
  public DetectionResult evaluate(double x) {
    if (Double.isNaN(x) || Double.isInfinite(x)) {
      throw new IllegalArgumentException("Data point must be a finite number, got: " + x);
    }

    lock.lock();
    try {
      if (window.size() < minSamples - 1) {
        window.add(x);
        return new DetectionResult.Warmup(x, window.size(), minSamples);
      }

      final var u = window.mean();
      final var s = window.stdDev();
      final var z = calculateZScore(x, u, s);

      final var status = (z > zThreshold) ? Status.ANOMALY : Status.OK;

      if (status == Status.OK || includeAnomaliesInWindow) {
        window.add(x);
      }

      return new DetectionResult.Scored(x, z, status);
    } finally {
      lock.unlock();
    }
  }

  private static double calculateZScore(double x, double mean, double stdDev) {
    if (stdDev == 0.0) {
      return (x == mean) ? 0.0 : Double.POSITIVE_INFINITY;
    }
    return Math.abs(x - mean) / stdDev;
  }

  /**
   * Return the current number of observations stored in the baseline window.
   *
   * @return sample count in the window
   */
  public int currentWindowSize() {
    lock.lock();
    try {
      return window.size();
    } finally {
      lock.unlock();
    }
  }
}
