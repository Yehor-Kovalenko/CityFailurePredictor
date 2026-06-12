package com.citydisruptors.incidentservice.config.kafka.producers;

import com.citydisruptors.incidentservice.config.kafka.events.IncidentCreatedEvent;
import com.citydisruptors.incidentservice.config.kafka.events.IncidentUpdatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Service
public class IncidentUpdatedProducer {

    private static final Logger log = LoggerFactory.getLogger(IncidentUpdatedProducer.class);

    private final KafkaTemplate<String, IncidentUpdatedEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    private final Counter published;
    private final Counter failed;
    private final Timer publishTimer;

    public IncidentUpdatedProducer(
            KafkaTemplate<String, IncidentUpdatedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.incidents-updated}") String topic,
            @Value("${app.kafka.producer-timeout-ms:5000}") long publishTimeoutMs,
            MeterRegistry registry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = Duration.ofMillis(publishTimeoutMs);

        this.published = registry.counter("incidents.updated.events.published");
        this.failed = registry.counter("incidents.updated.events.failed");
        this.publishTimer = Timer.builder("incidents.updated.kafka.publish.duration")
                .description("Kafka publish duration for updating incidents entries")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void publish(IncidentUpdatedEvent event) {
        publishTimer.record(() -> {
            try {
                var result = kafkaTemplate
                        .send(topic, event.incidentId(), event)
                        .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

                published.increment();

                log.info(
                        "Incident updated info published eventId={} incidentId={} topic={} partition={} offset={}",
                        event.eventId(),
                        event.incidentId(),
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );

            } catch (Exception ex) {
                failed.increment();

                log.error(
                        "Kafka publish failed eventId={} householdId={} topic={}",
                        event.eventId(),
                        event.incidentId(),
                        topic,
                        ex
                );

                throw new KafkaException("Failed to publish incident updated info to Kafka", ex);
            }
        });
    }

    public void flush() {
        kafkaTemplate.flush();
    }
}
