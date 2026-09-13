package com.vinigodoy.detector.output;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vinigodoy.detector.detection.DetectionResult;
import com.vinigodoy.detector.detection.Status;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

final class DetectionLoggerTest {

  private Locale defaultLocale;
  private ListAppender<ILoggingEvent> listAppender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    defaultLocale = Locale.getDefault();
    logger = (Logger) LoggerFactory.getLogger(DetectionLogger.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(defaultLocale);
    logger.detachAppender(listAppender);
  }

  @Test
  void formatWarmupResult() {
    final var result = new DetectionResult.Warmup(98.40, 3, 10);
    final var formatted = DetectionLogger.format(result);
    assertThat(formatted).isEqualTo("Data point: 98.40 | Status: WARMUP (3/10) | Z-score: n/a");
  }

  @Test
  void formatOkResult() {
    final var result = new DetectionResult.Scored(101.23, 0.12, Status.OK);
    final var formatted = DetectionLogger.format(result);
    assertThat(formatted).isEqualTo("Data point: 101.23 | Status: OK | Z-score: 0.12");
  }

  @Test
  void formatAnomalyResult() {
    final var result = new DetectionResult.Scored(171.90, 7.19, Status.ANOMALY);
    final var formatted = DetectionLogger.format(result);
    assertThat(formatted)
        .isEqualTo(
            "Data point: 171.90 | Status: ANOMALY DETECTED! | Z-score: 7.19 | ALERT: Significant"
                + " deviation detected.");
  }

  @Test
  void formatInfiniteZScore() {
    final var result = new DetectionResult.Scored(50.0, Double.POSITIVE_INFINITY, Status.ANOMALY);
    final var formatted = DetectionLogger.format(result);
    assertThat(formatted)
        .isEqualTo(
            "Data point: 50.00 | Status: ANOMALY DETECTED! | Z-score: Infinity | ALERT: Significant"
                + " deviation detected.");
  }

  @Test
  void formattingIsLocaleIndependent() {
    Locale.setDefault(Locale.GERMANY); // Uses comma instead of dot

    final var okResult = new DetectionResult.Scored(101.23, 0.12, Status.OK);
    assertThat(DetectionLogger.format(okResult))
        .isEqualTo("Data point: 101.23 | Status: OK | Z-score: 0.12");
  }

  @Test
  void logsAtCorrectLevels() {
    final var detectionLogger = new DetectionLogger();

    detectionLogger.log(new DetectionResult.Warmup(10.0, 1, 10));
    detectionLogger.log(new DetectionResult.Scored(10.0, 0.5, Status.OK));
    detectionLogger.log(new DetectionResult.Scored(100.0, 8.0, Status.ANOMALY));

    assertThat(listAppender.list).hasSize(3);
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(1).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(2).getLevel()).isEqualTo(Level.WARN);
  }
}
