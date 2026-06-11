package com.citydisruptors.decision_service.service;

import com.citydisruptors.decision_service.config.kafka.events.AlertCreatedEvent;
import com.citydisruptors.decision_service.config.kafka.events.PredictedReading;
import com.citydisruptors.decision_service.config.kafka.events.PredictionGeneratedEvent;
import com.citydisruptors.decision_service.config.kafka.producers.AlertProducer;
import com.citydisruptors.decision_service.entity.Decision;
import com.citydisruptors.decision_service.entity.DecisionResult;
import com.citydisruptors.decision_service.entity.RiskLevel;
import com.citydisruptors.decision_service.repository.DecisionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DecisionServiceTest {

    @Test
    void shouldCreateAlertForHighRiskPrediction() {
        DecisionRepository repository = mock(DecisionRepository.class);
        AlertProducer producer = mock(AlertProducer.class);

        when(repository.findByPredictionId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Decision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DecisionService service = new DecisionService(repository, producer, new SimpleMeterRegistry());

        PredictionGeneratedEvent event = predictionBatch(
                "batch-1",
                "MAC000008",
                new PredictedReading(
                        Instant.parse("2026-06-08T22:00:00Z"),
                        4.2,
                        0.91,
                        3.5,
                        5.1
                )
        );

        List<Decision> decisions = service.evaluatePrediction(event);

        assertThat(decisions).hasSize(1);

        Decision decision = decisions.getFirst();

        assertThat(decision.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(decision.getDecisionResult()).isEqualTo(DecisionResult.ALERT_CREATED);
        assertThat(decision.getAlertId()).isNotNull();

        verify(producer).publish(any(AlertCreatedEvent.class));
    }

    @Test
    void shouldNotCreateAlertForLowRiskPrediction() {
        DecisionRepository repository = mock(DecisionRepository.class);
        AlertProducer producer = mock(AlertProducer.class);

        when(repository.findByPredictionId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Decision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DecisionService service = new DecisionService(repository, producer, new SimpleMeterRegistry());

        PredictionGeneratedEvent event = predictionBatch(
                "batch-2",
                "MAC000008",
                new PredictedReading(
                        Instant.parse("2026-06-08T22:00:00Z"),
                        0.5,
                        0.8,
                        0.3,
                        0.8
                )
        );

        List<Decision> decisions = service.evaluatePrediction(event);

        assertThat(decisions).hasSize(1);

        Decision decision = decisions.getFirst();

        assertThat(decision.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(decision.getDecisionResult()).isEqualTo(DecisionResult.NO_ALERT);
        assertThat(decision.getAlertId()).isNull();

        verify(producer, never()).publish(any());
    }

    @Test
    void shouldSkipDuplicatePrediction() {
        DecisionRepository repository = mock(DecisionRepository.class);
        AlertProducer producer = mock(AlertProducer.class);

        Decision existing = mock(Decision.class);

        when(repository.findByPredictionId(anyString())).thenReturn(Optional.of(existing));

        DecisionService service = new DecisionService(repository, producer, new SimpleMeterRegistry());

        PredictionGeneratedEvent event = predictionBatch(
                "batch-3",
                "MAC000008",
                new PredictedReading(
                        Instant.parse("2026-06-08T22:00:00Z"),
                        4.2,
                        0.91,
                        3.5,
                        5.1
                )
        );

        List<Decision> result = service.evaluatePrediction(event);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isSameAs(existing);

        verify(repository, never()).save(any());
        verify(producer, never()).publish(any());
    }

    private PredictionGeneratedEvent predictionBatch(
            String batchId,
            String householdId,
            PredictedReading reading
    ) {
        return new PredictionGeneratedEvent(
                batchId,
                "ai-service",
                householdId,
                Instant.parse("2026-06-08T21:00:00Z"),
                "ngboost",
                "v1",
                List.of(reading)
        );
    }
}