package com.citydisruptors.decision_service.config.kafka.producers;

import com.citydisruptors.decision_service.config.kafka.events.AlertCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class AlertProducer {

    private final KafkaTemplate<String, AlertCreatedEvent> kafkaTemplate;
    private final String topic;
    private final Duration timeout;

    private final Counter published;
    private final Counter failed;
    private final Timer publishTimer;

    public AlertProducer(
            KafkaTemplate<String, AlertCreatedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.alerts-created}") String topic,
            @Value("${app.kafka.producer-timeout-ms:5000}") long timeoutMs,
            MeterRegistry registry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.timeout = Duration.ofMillis(timeoutMs);

        this.published = registry.counter("decision.alerts.published");
        this.failed = registry.counter("decision.alerts.failed");
        this.publishTimer = registry.timer("decision.kafka.publish.duration");
    }

    public void publish(AlertCreatedEvent event) {
        publishTimer.record(() -> {
            try {
                kafkaTemplate
                        .send(topic, event.householdId(), event)
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);

                published.increment();

            } catch (Exception ex) {
                failed.increment();
                throw new KafkaException("Failed to publish alert event", ex);
            }
        });
    }
}
