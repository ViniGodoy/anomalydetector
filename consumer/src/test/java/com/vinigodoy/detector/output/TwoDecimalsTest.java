package com.vinigodoy.detector.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

final class TwoDecimalsTest {

  @Test
  void formatsSpecialDoubleValues() {
    assertThat(TwoDecimals.format(Double.NaN)).isEqualTo("NaN");
    assertThat(TwoDecimals.format(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
    assertThat(TwoDecimals.format(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
  }

  @Test
  void formatsZeroAndNegativeZero() {
    assertThat(TwoDecimals.format(0.0)).isEqualTo("0.00");
    assertThat(TwoDecimals.format(-0.0)).isEqualTo("0.00");
    assertThat(TwoDecimals.format(-0.004)).isEqualTo("0.00");
  }

  @Test
  void formatsLargeValuesFallback() {
    final var huge = 1e18;
    assertThat(TwoDecimals.format(huge)).isEqualTo(String.format(Locale.ROOT, "%.2f", huge));
  }

  @Test
  void matchesStringFormatAcrossValues() {
    final var rng = RandomGenerator.of("L64X128MixRandom");
    final var magnitudes = new double[] {1.0, 10.0, 100.0, 1_000.0, 100_000.0, 10_000_000.0};

    for (final var mag : magnitudes) {
      for (var i = 0; i < 2_000; i++) {
        final var val = (rng.nextDouble() * 2.0 - 1.0) * mag;
        final var actual = TwoDecimals.format(val);
        final var rawExpected = String.format(Locale.ROOT, "%.2f", val);

        // String.format can produce "-0.00" on small negative values, TwoDecimals normalizes to
        // "0.00"
        final var expected = "-0.00".equals(rawExpected) ? "0.00" : rawExpected;

        assertThat(actual).as("Formatting discrepancy for value: %f", val).isEqualTo(expected);
      }
    }
  }
}
