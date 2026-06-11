package com.citydisruptors.decision_service.config.kafka.events;

import java.time.Instant;
import java.util.UUID;

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
    public static AlertCreatedEvent fromPrediction(
            String predictionId,
            PredictionGeneratedEvent batch,
            PredictedReading reading,
            String severity,
            Double riskScore,
            String message
    ) {
        String idBase = predictionId + ":" + severity;

        return new AlertCreatedEvent(
                UUID.nameUUIDFromBytes(idBase.getBytes()).toString(),
                predictionId,
                batch.predictionBatchId(),
                "decision-service",
                batch.householdId(),
                "ENERGY_CONSUMPTION_RISK",
                severity,
                message,
                riskScore,
                reading.predictedKwh(),
                reading.confidence(),
                reading.lowerBoundKwh(),
                reading.upperBoundKwh(),
                reading.targetTimestamp(),
                Instant.now()
        );
    }
}
