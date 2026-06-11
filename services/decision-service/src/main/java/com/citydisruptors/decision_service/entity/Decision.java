package com.citydisruptors.decision_service.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    private UUID id;

    @Column(name = "prediction_id", nullable = false, unique = true)
    private String predictionId;

    @Column(name = "prediction_batch_id", nullable = false)
    private String predictionBatchId;

    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Column(name = "prediction_timestamp", nullable = false)
    private Instant predictionTimestamp;

    @Column(name = "target_timestamp", nullable = false)
    private Instant targetTimestamp;

    @Column(name = "predicted_kwh", nullable = false)
    private Double predictedKwh;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "lower_bound_kwh")
    private Double lowerBoundKwh;

    @Column(name = "upper_bound_kwh")
    private Double upperBoundKwh;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_result", nullable = false)
    private DecisionResult decisionResult;

    @Column(name = "alert_id")
    private String alertId;

    @Column(name = "model_type")
    private String modelType;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Decision() {
    }

    public Decision(
            UUID id,
            String predictionId,
            String predictionBatchId,
            String householdId,
            Instant predictionTimestamp,
            Instant targetTimestamp,
            Double predictedKwh,
            Double confidence,
            Double lowerBoundKwh,
            Double upperBoundKwh,
            Double riskScore,
            RiskLevel riskLevel,
            DecisionResult decisionResult,
            String alertId,
            String modelType,
            String modelVersion,
            Instant createdAt
    ) {
        this.id = id;
        this.predictionId = predictionId;
        this.predictionBatchId = predictionBatchId;
        this.householdId = householdId;
        this.predictionTimestamp = predictionTimestamp;
        this.targetTimestamp = targetTimestamp;
        this.predictedKwh = predictedKwh;
        this.confidence = confidence;
        this.lowerBoundKwh = lowerBoundKwh;
        this.upperBoundKwh = upperBoundKwh;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.decisionResult = decisionResult;
        this.alertId = alertId;
        this.modelType = modelType;
        this.modelVersion = modelVersion;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPredictionId() {
        return predictionId;
    }

    public void setPredictionId(String predictionId) {
        this.predictionId = predictionId;
    }

    public String getPredictionBatchId() {
        return predictionBatchId;
    }

    public void setPredictionBatchId(String predictionBatchId) {
        this.predictionBatchId = predictionBatchId;
    }

    public String getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(String householdId) {
        this.householdId = householdId;
    }

    public Instant getPredictionTimestamp() {
        return predictionTimestamp;
    }

    public void setPredictionTimestamp(Instant predictionTimestamp) {
        this.predictionTimestamp = predictionTimestamp;
    }

    public Instant getTargetTimestamp() {
        return targetTimestamp;
    }

    public void setTargetTimestamp(Instant targetTimestamp) {
        this.targetTimestamp = targetTimestamp;
    }

    public Double getPredictedKwh() {
        return predictedKwh;
    }

    public void setPredictedKwh(Double predictedKwh) {
        this.predictedKwh = predictedKwh;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Double getLowerBoundKwh() {
        return lowerBoundKwh;
    }

    public void setLowerBoundKwh(Double lowerBoundKwh) {
        this.lowerBoundKwh = lowerBoundKwh;
    }

    public Double getUpperBoundKwh() {
        return upperBoundKwh;
    }

    public void setUpperBoundKwh(Double upperBoundKwh) {
        this.upperBoundKwh = upperBoundKwh;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public DecisionResult getDecisionResult() {
        return decisionResult;
    }

    public void setDecisionResult(DecisionResult decisionResult) {
        this.decisionResult = decisionResult;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
