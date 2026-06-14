package com.citydisruptors.notification_service.config.kafka.event;

import java.time.Instant;

public record AlertCreatedEvent(
        String alertId,
        String predictionId,
        String predictionBatchId,
        String source,
        String householdId,
        String alertType,
        String severity,
        String message,
        Double riskScore,
        Double predictedKwh,
        Double confidence,
        Double lowerBoundKwh,
        Double upperBoundKwh,
        Instant targetTimestamp,
        Instant createdAt
) {
}