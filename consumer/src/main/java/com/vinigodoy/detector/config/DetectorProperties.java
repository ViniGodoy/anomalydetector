package com.vinigodoy.detector.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("detector")
@Validated
public record DetectorProperties(
    @NotBlank String queueName,
    @Min(2) int windowSize,
    @Positive double zThreshold,
    @Min(2) int minSamples,
    boolean includeAnomaliesInWindow) {

  public DetectorProperties {
    if (queueName == null || queueName.isBlank()) {
      queueName = "data-points-detector";
    }
    if (zThreshold <= 0.0) {
      zThreshold = 3.0;
    }
  }

  @AssertTrue(message = "minSamples must be less than or equal to windowSize")
  public boolean isMinSamplesValid() {
    return minSamples <= windowSize;
  }
}
