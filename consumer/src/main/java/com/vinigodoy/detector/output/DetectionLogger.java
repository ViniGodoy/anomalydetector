package com.vinigodoy.detector.output;

import com.vinigodoy.detector.detection.DetectionResult;
import com.vinigodoy.detector.detection.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Formats and logs detection outcomes, according to the exact console output specification. */
@Component
public final class DetectionLogger {

  private static final Logger log = LoggerFactory.getLogger(DetectionLogger.class);

  /**
   * Produce the single-line string representation of a detection result.
   *
   * @param result the evaluation outcome
   * @return formatted message
   */
  public static String format(DetectionResult result) {
    return switch (result) {
      case DetectionResult.Warmup w ->
          "Data point: "
              + TwoDecimals.format(w.value())
              + " | Status: WARMUP ("
              + w.samples()
              + "/"
              + w.required()
              + ") | Z-score: n/a";
      case DetectionResult.Scored s when s.status() == Status.ANOMALY ->
          "Data point: "
              + TwoDecimals.format(s.value())
              + " | Status: ANOMALY DETECTED! | Z-score: "
              + formatZ(s.zScore())
              + " | ALERT: Significant deviation detected.";
      case DetectionResult.Scored s ->
          "Data point: "
              + TwoDecimals.format(s.value())
              + " | Status: OK | Z-score: "
              + formatZ(s.zScore());
    };
  }

  private static String formatZ(double z) {
    if (Double.isInfinite(z)) {
      return "Infinity";
    }
    return TwoDecimals.format(z);
  }

  /**
   * Log the result at WARN level for anomalies, and INFO for warmup/normal points.
   *
   * @param result evaluation outcome
   */
  public void log(DetectionResult result) {
    final var msg = format(result);
    if (result instanceof DetectionResult.Scored s && s.status() == Status.ANOMALY) {
      log.warn(msg);
    } else {
      log.info(msg);
    }
  }
}
