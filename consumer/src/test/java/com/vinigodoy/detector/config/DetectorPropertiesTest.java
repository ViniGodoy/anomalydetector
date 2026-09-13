package com.vinigodoy.detector.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DetectorPropertiesTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    try (final var factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void validPropertiesPassValidation() {
    final var properties = new DetectorProperties("data-points-queue", 50, 3.0, 10, false);
    final var violations = validator.validate(properties);
    assertThat(violations).isEmpty();
    assertThat(properties.queueName()).isEqualTo("data-points-queue");
    assertThat(properties.windowSize()).isEqualTo(50);
    assertThat(properties.zThreshold()).isEqualTo(3.0);
    assertThat(properties.minSamples()).isEqualTo(10);
    assertThat(properties.includeAnomaliesInWindow()).isFalse();
    assertThat(properties.isMinSamplesValid()).isTrue();
  }

  @Test
  void defaultValuesAppliedWhenEmpty() {
    final var properties = new DetectorProperties("", 50, 0.0, 10, false);
    assertThat(properties.queueName()).isEqualTo("data-points-detector");
    assertThat(properties.zThreshold()).isEqualTo(3.0);
  }

  @Test
  void minSamplesGreaterThanWindowSizeViolatesConstraint() {
    final var properties = new DetectorProperties("test-q", 10, 3.0, 20, false);
    final var violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
    assertThat(properties.isMinSamplesValid()).isFalse();
  }

  @Test
  void invalidThresholdAndWindowSizeViolateConstraints() {
    // Window size < 2 and zThreshold <= 0
    final var properties = new DetectorProperties("test-q", 1, -1.0, 1, false);
    final var violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
  }
}
