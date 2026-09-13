package com.vinigodoy.detector.messaging;

import com.vinigodoy.detector.detection.ZScoreDetector;
import com.vinigodoy.detector.output.DetectionLogger;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

@Component
public final class DataPointListener {

  private final ZScoreDetector detector;
  private final DetectionLogger detectionLogger;

  public DataPointListener(ZScoreDetector detector, DetectionLogger detectionLogger) {
    this.detector = detector;
    this.detectionLogger = detectionLogger;
  }

  @SqsListener(value = "${detector.queue-name}", acknowledgementMode = "SUCCESS")
  public void onMessage(DataPoint dataPoint) {
    final var result = detector.evaluate(dataPoint.value());
    detectionLogger.log(result);
  }
}
