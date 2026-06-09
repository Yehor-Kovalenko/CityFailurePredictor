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
        double score = 0.0;

        if (reading.predictedKwh() != null) {
            if (reading.predictedKwh() >= 5.0) score += 0.7;
            else if (reading.predictedKwh() >= 3.5) score += 0.55;
            else if (reading.predictedKwh() >= 2.5) score += 0.35;
            else score += 0.15;
        }

        if (reading.upperBoundKwh() != null) {
            if (reading.upperBoundKwh() >= 5.0) score += 0.25;
            else if (reading.upperBoundKwh() >= 3.5) score += 0.15;
        }

        if (reading.confidence() != null) {
            score *= reading.confidence();
        }

        return Math.min(score, 1.0);
    }

    private RiskLevel calculateRiskLevel(double riskScore, Double predictedKwh, Double upperBoundKwh) {
        double predicted = predictedKwh == null ? 0.0 : predictedKwh;
        double upper = upperBoundKwh == null ? 0.0 : upperBoundKwh;

        if (riskScore >= 0.90 || predicted >= 5.0 || upper >= 6.0) {
            return RiskLevel.CRITICAL;
        }

        if (riskScore >= 0.70 || predicted >= 3.5 || upper >= 4.5) {
            return RiskLevel.HIGH;
        }

        if (riskScore >= 0.45 || predicted >= 2.5) {
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
