package com.citydisruptors.decision_service.service;

import com.citydisruptors.decision_service.config.kafka.events.AlertCreatedEvent;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent.Forecast;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent.Observation;
import com.citydisruptors.decision_service.config.kafka.producers.AlertProducer;
import com.citydisruptors.decision_service.entity.RiskLevel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns traffic predictions into alerts. A traffic jam is treated as an anomaly:
 *
 * <ul>
 *   <li>TRAFFIC_ANOMALY_SURGE - far more vehicles than forecast for that hour (an unexpected jam),</li>
 *   <li>TRAFFIC_ANOMALY_DROP - far fewer vehicles than forecast (traffic stopped arriving),</li>
 *   <li>TRAFFIC_CONGESTION_EXPECTED - the forecast for the next hour reaches the congestion
 *       threshold of the junction; raised once when congestion starts, not every hour it lasts.</li>
 * </ul>
 *
 * <p>Alert ids are derived from the junction, the kind of alert and the hour, so a
 * redelivered prediction produces the same id and the notification service drops it.</p>
 */
@Service
public class TrafficDecisionService {

    static final String SURGE = "SURGE";
    static final String ALERT_SURGE = "TRAFFIC_ANOMALY_SURGE";
    static final String ALERT_DROP = "TRAFFIC_ANOMALY_DROP";
    static final String ALERT_CONGESTION = "TRAFFIC_CONGESTION_EXPECTED";
    static final String SUBJECT_PREFIX = "junction-";

    private final AlertProducer alertProducer;
    private final MeterRegistry registry;

    private final double criticalAnomalyScore;
    private final double criticalCongestionRatio;

    private final Map<String, Boolean> congestionActive = new ConcurrentHashMap<>();

    public TrafficDecisionService(
            AlertProducer alertProducer,
            MeterRegistry registry,
            @Value("${app.decision.traffic.critical-anomaly-score:6.0}") double criticalAnomalyScore,
            @Value("${app.decision.traffic.critical-congestion-ratio:1.25}") double criticalCongestionRatio
    ) {
        this.alertProducer = alertProducer;
        this.registry = registry;
        this.criticalAnomalyScore = criticalAnomalyScore;
        this.criticalCongestionRatio = criticalCongestionRatio;
    }

    public List<AlertCreatedEvent> evaluate(TrafficPredictionGeneratedEvent event) {
        validate(event);

        List<AlertCreatedEvent> alerts = new ArrayList<>();

        if (isAnomaly(event.observation())) {
            alerts.add(anomalyAlert(event, event.observation()));
        }

        Forecast congested = firstCongestedForecast(event);
        boolean alreadyActive = congestionActive.getOrDefault(event.junctionId(), false);

        if (congested != null && !alreadyActive) {
            alerts.add(congestionAlert(event, congested));
        }

        for (AlertCreatedEvent alert : alerts) {
            alertProducer.publish(alert);
            registry.counter("decision.traffic.alerts.created", "type", alert.alertType()).increment();
        }

        // Updated only after the alerts were published, so a failed publish is retried in full.
        if (congested != null) {
            congestionActive.put(event.junctionId(), true);
        } else {
            congestionActive.remove(event.junctionId());
        }

        registry.counter("decision.traffic.predictions.processed").increment();

        return alerts;
    }

    private boolean isAnomaly(Observation observation) {
        return observation != null
                && Boolean.TRUE.equals(observation.anomaly())
                && observation.timestamp() != null
                && observation.vehicles() != null
                && observation.expectedVehicles() != null;
    }

    private Forecast firstCongestedForecast(TrafficPredictionGeneratedEvent event) {
        if (event.predictions() == null) {
            return null;
        }

        return event.predictions().stream()
                .filter(forecast -> Boolean.TRUE.equals(forecast.congestionExpected()))
                .filter(forecast -> forecast.targetTimestamp() != null && forecast.predictedVehicles() != null)
                .findFirst()
                .orElse(null);
    }

    private AlertCreatedEvent anomalyAlert(TrafficPredictionGeneratedEvent event, Observation observation) {
        boolean surge = SURGE.equals(observation.anomalyType());
        double score = observation.anomalyScore() == null ? 0.0 : Math.abs(observation.anomalyScore());

        RiskLevel severity = score >= criticalAnomalyScore ? RiskLevel.CRITICAL : RiskLevel.HIGH;

        String message = String.format(
                Locale.ROOT,
                surge
                        ? "Unexpected congestion at junction %s: %.0f vehicles in the hour from %s, about %.0f expected."
                        : "Traffic at junction %s dropped to %.0f vehicles in the hour from %s, about %.0f expected; "
                        + "the road may be blocked.",
                event.junctionId(),
                observation.vehicles(),
                observation.timestamp(),
                observation.expectedVehicles()
        );

        return alert(
                event,
                surge ? ALERT_SURGE : ALERT_DROP,
                severity,
                message,
                Math.min(1.0, score / 10.0),
                null,
                observation.timestamp()
        );
    }

    private AlertCreatedEvent congestionAlert(TrafficPredictionGeneratedEvent event, Forecast forecast) {
        Double threshold = forecast.congestionThreshold();
        double ratio = threshold == null || threshold <= 0 ? 1.0 : forecast.predictedVehicles() / threshold;

        RiskLevel severity = ratio >= criticalCongestionRatio ? RiskLevel.CRITICAL : RiskLevel.HIGH;

        String message = String.format(
                Locale.ROOT,
                "Congestion expected at junction %s: about %.0f vehicles forecast for the hour from %s%s.",
                event.junctionId(),
                forecast.predictedVehicles(),
                forecast.targetTimestamp(),
                threshold == null ? "" : String.format(Locale.ROOT, " (congestion threshold %.0f)", threshold)
        );

        return alert(
                event,
                ALERT_CONGESTION,
                severity,
                message,
                Math.min(1.0, 0.65 + (ratio - 1.0)),
                forecast.confidence(),
                forecast.targetTimestamp()
        );
    }

    private AlertCreatedEvent alert(
            TrafficPredictionGeneratedEvent event,
            String alertType,
            RiskLevel severity,
            String message,
            double riskScore,
            Double confidence,
            Instant hour
    ) {
        String idBase = alertType + ":" + event.junctionId() + ":" + hour;

        return new AlertCreatedEvent(
                UUID.nameUUIDFromBytes(idBase.getBytes()).toString(),
                UUID.nameUUIDFromBytes((event.predictionBatchId() + ":" + idBase).getBytes()).toString(),
                event.predictionBatchId(),
                "decision-service",
                SUBJECT_PREFIX + event.junctionId(),
                alertType,
                severity.name(),
                message,
                riskScore,
                null,
                confidence,
                null,
                null,
                hour,
                Instant.now()
        );
    }

    private void validate(TrafficPredictionGeneratedEvent event) {
        if (event.predictionBatchId() == null || event.predictionBatchId().isBlank()) {
            throw new IllegalArgumentException("predictionBatchId is required");
        }

        if (event.junctionId() == null || event.junctionId().isBlank()) {
            throw new IllegalArgumentException("junctionId is required");
        }
    }
}
