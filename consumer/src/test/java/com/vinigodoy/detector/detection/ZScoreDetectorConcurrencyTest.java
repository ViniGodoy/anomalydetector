package com.vinigodoy.detector.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ZScoreDetectorConcurrencyTest {

  @Test
  void concurrentEvaluationMaintainsInvariants() throws InterruptedException {
    final var capacity = 50;
    final var minSamples = 10;
    final var detector = new ZScoreDetector(capacity, 3.0, minSamples, true);

    final var numThreads = 8;
    final var pointsPerThread = 50_000;
    final var totalPoints = numThreads * pointsPerThread;

    final var executor = Executors.newFixedThreadPool(numThreads);
    final var startLatch = new CountDownLatch(1);
    final var doneLatch = new CountDownLatch(numThreads);
    final var successCount = new AtomicInteger(0);

    for (var t = 0; t < numThreads; t++) {
      final var threadId = t;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (var i = 0; i < pointsPerThread; i++) {
                final var val = 100.0 + (threadId * 10.0) + (i % 5);
                final var res = detector.evaluate(val);
                if (res != null) {
                  successCount.incrementAndGet();
                }
              }
            } catch (final Exception e) {
              e.printStackTrace();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(successCount.get()).isEqualTo(totalPoints);
    assertThat(detector.currentWindowSize()).isEqualTo(capacity);
  }
}
