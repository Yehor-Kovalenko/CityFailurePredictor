package com.citydisruptors.decision_service.config.kafka.consumers;

import com.citydisruptors.decision_service.config.kafka.events.PredictionGeneratedEvent;
import com.citydisruptors.decision_service.service.DecisionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PredictionConsumer {

    private final DecisionService decisionService;
    private final Counter consumed;
    private final Counter failed;

    public PredictionConsumer(
            DecisionService decisionService,
            MeterRegistry registry
    ) {
        this.decisionService = decisionService;
        this.consumed = registry.counter("decision.predictions.consumed");
        this.failed = registry.counter("decision.predictions.failed");
    }

    @KafkaListener(
            topics = "${app.kafka.topics.predictions-generated}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "predictionKafkaListenerContainerFactory"
    )
    public void consume(PredictionGeneratedEvent event) {
        try {
            decisionService.evaluatePrediction(event);
            consumed.increment();
        } catch (Exception ex) {
            failed.increment();
            throw ex;
        }
    }
}
