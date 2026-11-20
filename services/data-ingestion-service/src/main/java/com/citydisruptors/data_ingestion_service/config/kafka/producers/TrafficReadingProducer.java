package com.citydisruptors.data_ingestion_service.config.kafka.producers;

import com.citydisruptors.data_ingestion_service.config.kafka.events.TrafficReadingEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class TrafficReadingProducer {

    private static final Logger log = LoggerFactory.getLogger(TrafficReadingProducer.class);

    private final KafkaTemplate<String, TrafficReadingEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    private final Counter published;
    private final Counter failed;

    public TrafficReadingProducer(
            KafkaTemplate<String, TrafficReadingEvent> kafkaTemplate,
            @Value("${app.kafka.topics.traffic-raw:traffic.readings.raw}") String topic,
            @Value("${app.kafka.producer-timeout-ms:5000}") long publishTimeoutMs,
            MeterRegistry registry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = Duration.ofMillis(publishTimeoutMs);

        this.published = registry.counter("ingestion.traffic.events.published");
        this.failed = registry.counter("ingestion.traffic.events.failed");
    }

    /**
     * Blocks until the broker confirms the event and throws when it does not,
     * so the caller never moves past a reading that was not published.
     */
    public void publish(TrafficReadingEvent event) {
        try {
            kafkaTemplate
                    .send(topic, event.junctionId(), event)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

            published.increment();

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failed.increment();
            throw new KafkaException("Interrupted while publishing traffic reading " + event.eventId(), ex);

        } catch (Exception ex) {
            failed.increment();

            log.error(
                    "Kafka publish failed eventId={} junctionId={} topic={}",
                    event.eventId(),
                    event.junctionId(),
                    topic,
                    ex
            );

            throw new KafkaException("Failed to publish traffic reading to Kafka", ex);
        }
    }
}
