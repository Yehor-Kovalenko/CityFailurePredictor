package com.citydisruptors.decision_service.config.kafka.events;

import java.time.Instant;
import java.util.List;

public record PredictionGeneratedEvent(
        String predictionBatchId,
        String source,
        String householdId,
        Instant generatedAt,
        String modelType,
        String modelVersion,
        List<PredictedReading> predictions
) {
}
