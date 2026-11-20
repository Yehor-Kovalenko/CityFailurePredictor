package com.citydisruptors.decision_service.config.kafka.consumers;

import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent;
import com.citydisruptors.decision_service.service.TrafficDecisionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TrafficPredictionConsumer {

    private final TrafficDecisionService decisionService;
    private final Counter consumed;
    private final Counter failed;

    public TrafficPredictionConsumer(
            TrafficDecisionService decisionService,
            MeterRegistry registry
    ) {
        this.decisionService = decisionService;
        this.consumed = registry.counter("decision.traffic.predictions.consumed");
        this.failed = registry.counter("decision.traffic.predictions.failed");
    }

    @KafkaListener(
            topics = "${app.kafka.topics.traffic-predictions-generated:traffic.predictions.generated}",
            groupId = "${spring.kafka.consumer.group-id}-traffic",
            containerFactory = "trafficPredictionKafkaListenerContainerFactory"
    )
    public void consume(TrafficPredictionGeneratedEvent event) {
        try {
            decisionService.evaluate(event);
            consumed.increment();
        } catch (RuntimeException ex) {
            failed.increment();
            throw ex;
        }
    }
}
