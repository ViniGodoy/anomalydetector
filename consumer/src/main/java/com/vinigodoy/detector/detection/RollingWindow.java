package com.vinigodoy.detector.detection;

/**
 * High-performance O(1) ring buffer maintaining running sums for rolling mean and variance.
 *
 * <p>To prevent cumulative floating-point rounding drift over millions of streaming observations,
 * running sums are periodically recomputed directly from buffer elements every {@code 10 *
 * capacity} insertions (amortized O(1)).
 */
public final class RollingWindow {

  private static final int RECALCULATION_FACTOR = 10;

  private final double[] ring;
  private final int capacity;
  private int head;
  private int size;
  private double sum;
  private double sumSq;
  private int recalcAdditions;

  /**
   * Construct a RollingWindow with the given maximum capacity.
   *
   * @param capacity maximum number of elements retained in the window (minimum 2)
   */
  public RollingWindow(int capacity) {
    if (capacity < 2) {
      throw new IllegalArgumentException("Capacity must be at least 2, got: " + capacity);
    }
    this.capacity = capacity;
    this.ring = new double[capacity];
    this.head = 0;
    this.size = 0;
    this.sum = 0.0;
    this.sumSq = 0.0;
    this.recalcAdditions = 0;
  }

  /**
   * Append a new numerical observation to the window, evicting the oldest element if at capacity.
   *
   * @param x observation value
   * @throws IllegalArgumentException if x is NaN or Infinite
   */
  public void add(double x) {
    if (Double.isNaN(x) || Double.isInfinite(x)) {
      throw new IllegalArgumentException("Observation must be a finite number, got: " + x);
    }

    if (size == capacity) {
      final var evicted = ring[head];
      sum -= evicted;
      sumSq -= evicted * evicted;
    } else {
      size++;
    }

    ring[head] = x;
    sum += x;
    sumSq += x * x;

    head = (head + 1) % capacity;
    recalcAdditions++;

    if (recalcAdditions >= RECALCULATION_FACTOR * capacity) {
      recalculateSums();
      recalcAdditions = 0;
    }
  }

  private void recalculateSums() {
    var s = 0.0;
    var sq = 0.0;
    for (var i = 0; i < size; i++) {
      final var v = ring[i];
      s += v;
      sq += v * v;
    }
    sum = s;
    sumSq = sq;
  }

  /**
   * Compute the population by mean of observations in the window.
   *
   * @return population mean
   * @throws IllegalStateException if the window is empty
   */
  public double mean() {
    if (size == 0) {
      throw new IllegalStateException("Cannot compute mean of an empty window");
    }
    return sum / size;
  }

  /**
   * Compute the population variance of observations in the window. Clamped to 0 to eliminate tiny
   * negative values caused by floating-point arithmetic.
   *
   * @return population variance
   * @throws IllegalStateException if window is empty
   */
  public double variance() {
    if (size == 0) {
      throw new IllegalStateException("Cannot compute variance of an empty window");
    }
    final var m = sum / size;
    final var calculatedVariance = (sumSq / size) - (m * m);
    return Math.max(0.0, calculatedVariance);
  }

  /**
   * Compute the population standard deviation of observations in the window.
   *
   * @return population standard deviation
   * @throws IllegalStateException if the window is empty
   */
  public double stdDev() {
    return Math.sqrt(variance());
  }

  /**
   * The current number of observations currently in the window.
   *
   * @return sample count
   */
  public int size() {
    return size;
  }

  /**
   * Maximum capacity of the window.
   *
   * @return capacity
   */
  public int capacity() {
    return capacity;
  }

  /**
   * Whether the window has no observations yet.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return size == 0;
  }
}
