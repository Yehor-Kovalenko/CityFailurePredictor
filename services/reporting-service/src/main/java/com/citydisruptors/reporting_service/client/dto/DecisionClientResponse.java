package com.citydisruptors.reporting_service.client.dto;

import com.citydisruptors.reporting_service.entity.DecisionResult;
import com.citydisruptors.reporting_service.entity.RiskLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionClientResponse(
        UUID decisionId,
        String predictionId,
        String predictionBatchId,
        String householdId,
        Instant targetTimestamp,
        Double predictedKwh,
        Double confidence,
        Double lowerBoundKwh,
        Double upperBoundKwh,
        Double riskScore,
        RiskLevel riskLevel,
        DecisionResult decisionResult,
        String alertId,
        Instant createdAt
) {
}
