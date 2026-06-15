package com.citydisruptors.decision_service.service;

import com.citydisruptors.decision_service.config.kafka.events.AlertCreatedEvent;
import com.citydisruptors.decision_service.config.kafka.events.PredictedReading;
import com.citydisruptors.decision_service.config.kafka.events.PredictionGeneratedEvent;
import com.citydisruptors.decision_service.config.kafka.producers.AlertProducer;
import com.citydisruptors.decision_service.entity.Decision;
import com.citydisruptors.decision_service.entity.DecisionResult;
import com.citydisruptors.decision_service.entity.RiskLevel;
import com.citydisruptors.decision_service.repository.DecisionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DecisionService {

    private final DecisionRepository repository;
    private final AlertProducer alertProducer;

    private final Counter predictionsProcessed;
    private final Counter alertsCreated;
    private final Counter duplicatesSkipped;
    private final MeterRegistry registry;

    @Value("${app.decision.thresholds.medium-kwh:1.2}")
    private double mediumKwh;

    @Value("${app.decision.thresholds.high-kwh:1.8}")
    private double highKwh;

    @Value("${app.decision.thresholds.critical-kwh:2.5}")
    private double criticalKwh;

    @Value("${app.decision.thresholds.high-risk-score:0.65}")
    private double highRiskScore;

    @Value("${app.decision.thresholds.critical-risk-score:0.85}")
    private double criticalRiskScore;

    public DecisionService(
            DecisionRepository repository,
            AlertProducer alertProducer,
            MeterRegistry registry
    ) {
        this.repository = repository;
        this.alertProducer = alertProducer;

        this.predictionsProcessed = registry.counter("decision.predictions.processed");
        this.alertsCreated = registry.counter("decision.alerts.created");
        this.duplicatesSkipped = registry.counter("decision.predictions.duplicates.skipped");
        this.registry = registry;
    }

    @Transactional
    public List<Decision> evaluatePrediction(PredictionGeneratedEvent batch) {
        validateBatch(batch);

        return batch.predictions()
                .stream()
                .map(reading -> evaluateSinglePrediction(batch, reading))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Decision> getDecisions() {
        return repository.findAll();
    }

    private Decision evaluateSinglePrediction(PredictionGeneratedEvent batch, PredictedReading reading) {
        String predictionId = buildPredictionId(batch, reading);

        var existing = repository.findByPredictionId(predictionId);
        if (existing.isPresent()) {
            duplicatesSkipped.increment();
            return existing.get();
        }

        double riskScore = calculateRiskScore(reading);
        RiskLevel riskLevel = calculateRiskLevel(riskScore, reading.predictedKwh(), reading.upperBoundKwh());

        registry.counter(
                "decision.risk.level.total",
                "level", riskLevel.name()
        ).increment();

        boolean shouldCreateAlert = riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;

        AlertCreatedEvent alert = null;

        if (shouldCreateAlert) {
            alert = AlertCreatedEvent.fromPrediction(
                    predictionId,
                    batch,
                    reading,
                    riskLevel.name(),
                    riskScore,
                    buildAlertMessage(batch, reading, riskLevel, riskScore)
            );
        }

        Decision decision = new Decision(
                UUID.randomUUID(),
                predictionId,
                batch.predictionBatchId(),
                batch.householdId(),
                batch.generatedAt(),
                reading.targetTimestamp(),
                reading.predictedKwh(),
                reading.confidence(),
                reading.lowerBoundKwh(),
                reading.upperBoundKwh(),
                riskScore,
                riskLevel,
                shouldCreateAlert ? DecisionResult.ALERT_CREATED : DecisionResult.NO_ALERT,
                alert == null ? null : alert.alertId(),
                batch.modelType(),
                batch.modelVersion(),
                Instant.now()
        );

        Decision saved = repository.save(decision);
        predictionsProcessed.increment();

        if (alert != null) {
            alertProducer.publish(alert);
            alertsCreated.increment();
        }

        return saved;
    }

    private String buildPredictionId(PredictionGeneratedEvent batch, PredictedReading reading) {
        String idBase = batch.predictionBatchId() + ":" + batch.householdId() + ":" + reading.targetTimestamp();
        return UUID.nameUUIDFromBytes(idBase.getBytes()).toString();
    }

    private double calculateRiskScore(PredictedReading reading) {
        double predicted = reading.predictedKwh() == null ? 0.0 : reading.predictedKwh();
        double upper = reading.upperBoundKwh() == null ? predicted : reading.upperBoundKwh();

        double score = 0.10;

        if (predicted >= criticalKwh) score += 0.75;
        else if (predicted >= highKwh) score += 0.55;
        else if (predicted >= mediumKwh) score += 0.35;
        else score += 0.10;

        if (upper >= criticalKwh) score += 0.20;
        else if (upper >= highKwh) score += 0.15;
        else if (upper >= mediumKwh) score += 0.05;

        if (reading.confidence() != null) {
            score *= Math.max(0.5, reading.confidence());
        }

        return Math.min(score, 1.0);
    }

    private RiskLevel calculateRiskLevel(double riskScore, Double predictedKwh, Double upperBoundKwh) {
        double predicted = predictedKwh == null ? 0.0 : predictedKwh;
        double upper = upperBoundKwh == null ? 0.0 : upperBoundKwh;

        if (riskScore >= criticalRiskScore || predicted >= criticalKwh || upper >= criticalKwh) {
            return RiskLevel.CRITICAL;
        }

        if (riskScore >= highRiskScore || predicted >= highKwh || upper >= highKwh) {
            return RiskLevel.HIGH;
        }

        if (predicted >= mediumKwh || upper >= mediumKwh) {
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }

    private String buildAlertMessage(
            PredictionGeneratedEvent batch,
            PredictedReading reading,
            RiskLevel riskLevel,
            double riskScore
    ) {
        return "Detected " + riskLevel +
                " electricity consumption risk for household " + batch.householdId() +
                ". Predicted kWh: " + reading.predictedKwh() +
                ", confidence: " + reading.confidence() +
                ", range: [" + reading.lowerBoundKwh() + ", " + reading.upperBoundKwh() + "]" +
                ", risk score: " + riskScore;
    }

    private void validateBatch(PredictionGeneratedEvent batch) {
        if (batch.predictionBatchId() == null || batch.predictionBatchId().isBlank()) {
            throw new IllegalArgumentException("predictionBatchId is required");
        }

        if (batch.householdId() == null || batch.householdId().isBlank()) {
            throw new IllegalArgumentException("householdId is required");
        }

        if (batch.generatedAt() == null) {
            throw new IllegalArgumentException("generatedAt is required");
        }

        if (batch.predictions() == null || batch.predictions().isEmpty()) {
            throw new IllegalArgumentException("predictions list cannot be empty");
        }

        for (PredictedReading reading : batch.predictions()) {
            if (reading.targetTimestamp() == null) {
                throw new IllegalArgumentException("targetTimestamp is required");
            }

            if (reading.predictedKwh() == null || reading.predictedKwh() < 0) {
                throw new IllegalArgumentException("predictedKwh must be non-negative");
            }

            if (reading.confidence() != null && (reading.confidence() < 0 || reading.confidence() > 1)) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
        }
    }
}
