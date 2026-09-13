package com.vinigodoy.detector.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class DataPointDeserializationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void deserializesProducerPayload() throws Exception {
    final var json =
        "{\"id\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"timestamp\":\"2026-09-13T12:00:00+00:00\",\"value\":101.23}";

    final var dataPoint = objectMapper.readValue(json, DataPoint.class);

    assertThat(dataPoint.id()).isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    assertThat(dataPoint.timestamp()).isEqualTo(Instant.parse("2026-09-13T12:00:00Z"));
    assertThat(dataPoint.value()).isEqualTo(101.23);
  }
}
