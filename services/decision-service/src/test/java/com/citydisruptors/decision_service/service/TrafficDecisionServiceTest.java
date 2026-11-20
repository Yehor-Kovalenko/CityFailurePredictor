package com.citydisruptors.decision_service.service;

import com.citydisruptors.decision_service.config.kafka.events.AlertCreatedEvent;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent.Forecast;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent.Observation;
import com.citydisruptors.decision_service.config.kafka.producers.AlertProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TrafficDecisionServiceTest {

    private static final Instant NINE = Instant.parse("2017-03-06T09:00:00Z");
    private static final Instant TEN = Instant.parse("2017-03-06T10:00:00Z");

    private AlertProducer producer;
    private TrafficDecisionService service;

    @BeforeEach
    void setUp() {
        producer = mock(AlertProducer.class);
        service = new TrafficDecisionService(producer, new SimpleMeterRegistry(), 6.0, 1.25);
    }

    @Test
    void normalTrafficCreatesNoAlert() {
        List<AlertCreatedEvent> alerts = service.evaluate(event("3", observation(42, 40, false, null, 0.8), forecast(41, 89, false)));

        assertThat(alerts).isEmpty();
        verify(producer, times(0)).publish(any(AlertCreatedEvent.class));
    }

    @Test
    void jamIsReportedAsSurgeAlert() {
        List<AlertCreatedEvent> alerts = service.evaluate(event("3", observation(95, 40, true, "SURGE", 22.0), forecast(60, 89, false)));

        assertThat(alerts).hasSize(1);
        AlertCreatedEvent alert = alerts.getFirst();
        assertThat(alert.alertType()).isEqualTo("TRAFFIC_ANOMALY_SURGE");
        assertThat(alert.severity()).isEqualTo("CRITICAL");
        assertThat(alert.householdId()).isEqualTo("junction-3");
        assertThat(alert.targetTimestamp()).isEqualTo(NINE);
        assertThat(alert.riskScore()).isEqualTo(1.0);
        assertThat(alert.predictedKwh()).isNull();
        assertThat(alert.message()).contains("junction 3", "95 vehicles", "about 40 expected");
        verify(producer).publish(alert);
    }

    @Test
    void trafficThatStopsIsReportedAsDropAlert() {
        AlertCreatedEvent alert = service.evaluate(
                event("1", observation(2, 40, true, "DROP", -4.5), forecast(30, 89, false))).getFirst();

        assertThat(alert.alertType()).isEqualTo("TRAFFIC_ANOMALY_DROP");
        assertThat(alert.severity()).isEqualTo("HIGH");
        assertThat(alert.riskScore()).isEqualTo(0.45);
        assertThat(alert.message()).contains("may be blocked");
    }

    @Test
    void expectedCongestionIsAlertedOnceUntilItClears() {
        assertThat(service.evaluate(event("2", null, forecast(31, 30, true))))
                .extracting(AlertCreatedEvent::alertType).containsExactly("TRAFFIC_CONGESTION_EXPECTED");
        assertThat(service.evaluate(event("2", null, forecast(33, 30, true)))).isEmpty();
        assertThat(service.evaluate(event("2", null, forecast(20, 30, false)))).isEmpty();
        assertThat(service.evaluate(event("2", null, forecast(32, 30, true)))).hasSize(1);
    }

    @Test
    void congestionSeverityGrowsWithTheForecast() {
        assertThat(service.evaluate(event("1", null, forecast(90, 89, true))).getFirst().severity()).isEqualTo("HIGH");
        assertThat(service.evaluate(event("2", null, forecast(40, 30, true))).getFirst().severity()).isEqualTo("CRITICAL");
    }

    @Test
    void junctionsAreTrackedSeparately() {
        service.evaluate(event("1", null, forecast(95, 89, true)));

        assertThat(service.evaluate(event("2", null, forecast(35, 30, true)))).hasSize(1);
    }

    @Test
    void redeliveredPredictionGetsTheSameAlertIds() {
        TrafficPredictionGeneratedEvent jam = event("3", observation(95, 40, true, "SURGE", 22.0), forecast(31, 30, true));

        List<AlertCreatedEvent> first = service.evaluate(jam);
        List<AlertCreatedEvent> second = new TrafficDecisionService(producer, new SimpleMeterRegistry(), 6.0, 1.25)
                .evaluate(jam);

        assertThat(first).hasSize(2);
        assertThat(second).extracting(AlertCreatedEvent::alertId)
                .containsExactlyElementsOf(first.stream().map(AlertCreatedEvent::alertId).toList());
    }

    @Test
    void failedPublishLeavesCongestionToBeAlertedOnRetry() {
        doThrow(new KafkaException("broker down")).when(producer).publish(any(AlertCreatedEvent.class));
        TrafficPredictionGeneratedEvent congested = event("2", null, forecast(31, 30, true));

        assertThatThrownBy(() -> service.evaluate(congested)).isInstanceOf(KafkaException.class);

        doNothing().when(producer).publish(any(AlertCreatedEvent.class));
        assertThat(service.evaluate(congested)).hasSize(1);
    }

    @Test
    void eventWithoutJunctionIsRejected() {
        assertThatThrownBy(() -> service.evaluate(event(" ", null, forecast(10, 30, false))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesTheJsonPublishedByTheAiService() throws Exception {
        String json = """
                {"predictionBatchId": "b-1", "source": "ai-service", "junctionId": "3",
                 "generatedAt": "2017-03-06T09:00:01.123456Z", "modelType": "xgboost", "modelVersion": "2026-06-14_13-36-24",
                 "observation": {"timestamp": "2017-03-06T09:00:00Z", "vehicles": 95.0, "expectedVehicles": 40.0,
                                 "lowerBoundVehicles": 35.1, "upperBoundVehicles": 44.9, "anomaly": true,
                                 "anomalyType": "SURGE", "anomalyScore": 22.0},
                 "predictions": [{"targetTimestamp": "2017-03-06T10:00:00Z", "predictedVehicles": 60.5, "confidence": 0.95,
                                  "lowerBoundVehicles": 55.6, "upperBoundVehicles": 65.4, "congestionThreshold": 30,
                                  "congestionExpected": true, "futureField": 1}]}
                """;

        TrafficPredictionGeneratedEvent parsed = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .readValue(json, TrafficPredictionGeneratedEvent.class);

        assertThat(parsed.observation().timestamp()).isEqualTo(NINE);
        assertThat(parsed.predictions().getFirst().congestionThreshold()).isEqualTo(30.0);
        assertThat(service.evaluate(parsed)).extracting(AlertCreatedEvent::alertType)
                .containsExactly("TRAFFIC_ANOMALY_SURGE", "TRAFFIC_CONGESTION_EXPECTED");

        TrafficPredictionGeneratedEvent withoutObservation = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .readValue(json.replaceAll("(?s)\"observation\": \\{.*?\\},", "\"observation\": null,"),
                        TrafficPredictionGeneratedEvent.class);
        assertThat(withoutObservation.observation()).isNull();
    }

    private static TrafficPredictionGeneratedEvent event(String junctionId, Observation observation, Forecast forecast) {
        return new TrafficPredictionGeneratedEvent(
                "batch-" + junctionId, "ai-service", junctionId, NINE, "xgboost", "v1", observation, List.of(forecast));
    }

    private static Observation observation(double vehicles, double expected, boolean anomaly, String type, double score) {
        return new Observation(NINE, vehicles, expected, expected - 5, expected + 5, anomaly, type, score);
    }

    private static Forecast forecast(double predicted, double threshold, boolean congestionExpected) {
        return new Forecast(TEN, predicted, 0.95, predicted - 5, predicted + 5, threshold, congestionExpected);
    }
}
