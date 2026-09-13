package com.vinigodoy.detector.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

final class RollingWindowTest {

  @Test
  void invalidCapacityThrowsException() {
    assertThatThrownBy(() -> new RollingWindow(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Capacity must be at least 2");

    assertThatThrownBy(() -> new RollingWindow(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void emptyWindowQueriesThrowException() {
    final var window = new RollingWindow(5);
    assertThat(window.isEmpty()).isTrue();
    assertThat(window.size()).isEqualTo(0);
    assertThat(window.capacity()).isEqualTo(5);

    assertThatThrownBy(window::mean)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty window");

    assertThatThrownBy(window::variance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty window");

    assertThatThrownBy(window::stdDev).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsNanAndInfinity() {
    final var window = new RollingWindow(5);

    assertThatThrownBy(() -> window.add(Double.NaN)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> window.add(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> window.add(Double.NEGATIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void statisticalCalculationKnownDataset() {
    // Known dataset: [2, 4, 4, 4, 5, 5, 7, 9] -> mean = 5.0, population stddev = 2.0
    final var window = new RollingWindow(10);
    final var data = new double[] {2, 4, 4, 4, 5, 5, 7, 9};
    for (final var v : data) {
      window.add(v);
    }

    assertThat(window.isEmpty()).isFalse();
    assertThat(window.size()).isEqualTo(8);
    assertThat(window.mean()).isCloseTo(5.0, within(1e-9));
    assertThat(window.variance()).isCloseTo(4.0, within(1e-9));
    assertThat(window.stdDev()).isCloseTo(2.0, within(1e-9));
  }

  @Test
  void identicalElementsProduceZeroVariance() {
    final var window = new RollingWindow(5);
    window.add(10.0);
    window.add(10.0);
    window.add(10.0);

    assertThat(window.mean()).isEqualTo(10.0);
    assertThat(window.variance()).isEqualTo(0.0);
    assertThat(window.stdDev()).isEqualTo(0.0);
  }

  @Test
  void evictionAtCapacityAndRecalculation() {
    final var capacity = 4;
    final var window = new RollingWindow(capacity);

    // Add 4 elements: [10, 20, 30, 40]
    window.add(10.0);
    window.add(20.0);
    window.add(30.0);
    window.add(40.0);

    assertThat(window.size()).isEqualTo(4);
    assertThat(window.mean()).isEqualTo(25.0);

    // Add 5th element: evicts 10 -> [20, 30, 40, 50]
    window.add(50.0);
    assertThat(window.size()).isEqualTo(4);
    assertThat(window.mean()).isEqualTo(35.0);

    // Add enough elements to trigger recalculateSums() multiple times (10 * capacity = 40)
    for (var i = 0; i < 50; i++) {
      window.add(100.0);
    }
    assertThat(window.size()).isEqualTo(4);
    assertThat(window.mean()).isEqualTo(100.0);
    assertThat(window.stdDev()).isEqualTo(0.0);
  }
}
