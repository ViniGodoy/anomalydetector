package com.vinigodoy.detector.output;

import java.util.Locale;

/**
 * Fast, allocation-conscious formatter producing 2-decimal-place numbers equivalent to {@code
 * String.format(Locale.ROOT, "%.2f", val)}.
 */
public final class TwoDecimals {

  private TwoDecimals() {}

  /**
   * Format a double value into the provided StringBuilder.
   *
   * @param val value to format
   * @param sb target StringBuilder
   */
  public static void format(double val, StringBuilder sb) {
    if (Double.isNaN(val)) {
      sb.append("NaN");
      return;
    }
    if (Double.isInfinite(val)) {
      if (val < 0.0) {
        sb.append("-Infinity");
      } else {
        sb.append("Infinity");
      }
      return;
    }

    // For values beyond long capacity with 2 decimals, fall back to standard format
    if (Math.abs(val) > 9e16) {
      sb.append(String.format(Locale.ROOT, "%.2f", val));
      return;
    }

    final var rawScaled = Math.round(val * 100.0);
    if (rawScaled == 0) {
      sb.append("0.00");
      return;
    }

    if (rawScaled < 0) {
      sb.append('-');
    }
    final var scaled = Math.abs(rawScaled);

    final var integerPart = scaled / 100;
    final var fractionPart = scaled % 100;

    sb.append(integerPart).append('.');
    if (fractionPart < 10) {
      sb.append('0');
    }
    sb.append(fractionPart);
  }

  /**
   * Format a double value into a new String.
   *
   * @param val value to format
   * @return formatted 2-decimal string
   */
  public static String format(double val) {
    final var sb = new StringBuilder(16);
    format(val, sb);
    return sb.toString();
  }
}
