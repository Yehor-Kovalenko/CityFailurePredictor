package com.citydisruptors.decision_service.api.dto;

import com.citydisruptors.decision_service.entity.Decision;
import com.citydisruptors.decision_service.entity.DecisionResult;
import com.citydisruptors.decision_service.entity.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
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
    public static AlertResponse from(Decision decision) {
        return new AlertResponse(
                decision.getId(),
                decision.getPredictionId(),
                decision.getPredictionBatchId(),
                decision.getHouseholdId(),
                decision.getTargetTimestamp(),
                decision.getPredictedKwh(),
                decision.getConfidence(),
                decision.getLowerBoundKwh(),
                decision.getUpperBoundKwh(),
                decision.getRiskScore(),
                decision.getRiskLevel(),
                decision.getDecisionResult(),
                decision.getAlertId(),
                decision.getCreatedAt()
        );
    }
}
