package com.citydisruptors.decision_service.config.kafka.events;

import java.time.Instant;

public record PredictedReading(
        Instant targetTimestamp,
        Double predictedKwh,
        Double confidence,
        Double lowerBoundKwh,
        Double upperBoundKwh
) {
}
