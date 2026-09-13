package com.vinigodoy.detector.messaging;

import java.time.Instant;

public record DataPoint(String id, Instant timestamp, double value) {}
