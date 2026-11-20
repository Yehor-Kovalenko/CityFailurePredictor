package com.citydisruptors.decision_service.config.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Published by the AI service on traffic.predictions.generated for every
 * traffic reading: how the reading compares with the forecast made for its
 * hour, and the forecast for the next hour.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrafficPredictionGeneratedEvent(
        String predictionBatchId,
        String source,
        String junctionId,
        Instant generatedAt,
        String modelType,
        String modelVersion,
        Observation observation,
        List<Forecast> predictions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Observation(
            Instant timestamp,
            Double vehicles,
            Double expectedVehicles,
            Double lowerBoundVehicles,
            Double upperBoundVehicles,
            Boolean anomaly,
            String anomalyType,
            Double anomalyScore
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(
            Instant targetTimestamp,
            Double predictedVehicles,
            Double confidence,
            Double lowerBoundVehicles,
            Double upperBoundVehicles,
            Double congestionThreshold,
            Boolean congestionExpected
    ) {
    }
}
