package com.citydisruptors.data_ingestion_service.config.kafka.producers;

import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
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
public class ElectricityReadingProducer {

    private static final Logger log = LoggerFactory.getLogger(ElectricityReadingProducer.class);

    private final KafkaTemplate<String, ElectricityReadingEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    private final Counter published;
    private final Counter failed;
    private final Timer publishTimer;

    public ElectricityReadingProducer(
            KafkaTemplate<String, ElectricityReadingEvent> kafkaTemplate,
            @Value("${app.kafka.topics.electricity-raw}") String topic,
            @Value("${app.kafka.producer-timeout-ms:5000}") long publishTimeoutMs,
            MeterRegistry registry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = Duration.ofMillis(publishTimeoutMs);

        this.published = registry.counter("ingestion.electricity.events.published");
        this.failed = registry.counter("ingestion.electricity.events.failed");
        this.publishTimer = Timer.builder("ingestion.electricity.kafka.publish.duration")
                .description("Kafka publish duration for electricity readings")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void publish(ElectricityReadingEvent event) {
        publishTimer.record(() -> {
            try {
                var result = kafkaTemplate
                        .send(topic, event.householdId(), event)
                        .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

                published.increment();

                log.info(
                        "Electricity reading published eventId={} householdId={} topic={} partition={} offset={}",
                        event.eventId(),
                        event.householdId(),
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );

            } catch (Exception ex) {
                failed.increment();

                log.error(
                        "Kafka publish failed eventId={} householdId={} topic={}",
                        event.eventId(),
                        event.householdId(),
                        topic,
                        ex
                );

                throw new KafkaException("Failed to publish electricity reading to Kafka", ex);
            }
        });
    }

    public void flush() {
        kafkaTemplate.flush();
    }
}
