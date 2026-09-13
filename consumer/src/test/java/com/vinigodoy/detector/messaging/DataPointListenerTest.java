package com.vinigodoy.detector.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vinigodoy.detector.detection.DetectionResult;
import com.vinigodoy.detector.detection.Status;
import com.vinigodoy.detector.detection.ZScoreDetector;
import com.vinigodoy.detector.output.DetectionLogger;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class DataPointListenerTest {

  @Mock private ZScoreDetector detector;
  @Mock private DetectionLogger detectionLogger;

  @InjectMocks private DataPointListener listener;

  @Test
  void onMessageProcessesAndLogsResult() {
    final var dataPoint = new DataPoint("id-123", Instant.now(), 105.5);
    final var result = new DetectionResult.Scored(105.5, 0.55, Status.OK);

    when(detector.evaluate(105.5)).thenReturn(result);

    listener.onMessage(dataPoint);

    verify(detector).evaluate(105.5);
    verify(detectionLogger).log(result);
  }

  @Test
  void exceptionsFromDetectorPropagate() {
    final var dataPoint = new DataPoint("id-err", Instant.now(), 999.0);
    when(detector.evaluate(999.0)).thenThrow(new RuntimeException("Simulated detector error"));

    assertThatThrownBy(() -> listener.onMessage(dataPoint))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated detector error");
  }
}
