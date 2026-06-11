package com.citydisruptors.decision_service.api.controller.rest;

import com.citydisruptors.decision_service.api.dto.AlertResponse;
import com.citydisruptors.decision_service.api.dto.TestPredictionRequest;
import com.citydisruptors.decision_service.config.kafka.events.PredictedReading;
import com.citydisruptors.decision_service.config.kafka.events.PredictionGeneratedEvent;
import com.citydisruptors.decision_service.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/decision")
public class DecisionRESTController {

    private final DecisionService decisionService;

    public DecisionRESTController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @GetMapping("/test")
    public String test() {
        return "DECISION SERVICE OK";
    }

    @GetMapping("/decisions")
    public List<AlertResponse> getDecisions() {
        return decisionService.getDecisions()
                .stream()
                .map(AlertResponse::from)
                .toList();
    }

    @PostMapping("/test-prediction")
    public List<AlertResponse> testPrediction(@RequestBody @Valid TestPredictionRequest request) {
        PredictionGeneratedEvent event = new PredictionGeneratedEvent(
                UUID.randomUUID().toString(),
                "manual-test",
                request.householdId(),
                Instant.now(),
                "manual",
                "test",
                List.of(new PredictedReading(
                        request.targetTimestamp(),
                        request.predictedKwh(),
                        request.confidence(),
                        request.lowerBoundKwh(),
                        request.upperBoundKwh()
                ))
        );

        return decisionService.evaluatePrediction(event)
                .stream()
                .map(AlertResponse::from)
                .toList();
    }
}
