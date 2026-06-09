package com.citydisruptors.decision_service.api.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record TestPredictionRequest(
        @NotBlank String householdId,
        @NotNull Instant targetTimestamp,
        @NotNull @PositiveOrZero Double predictedKwh,
        @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        Double lowerBoundKwh,
        Double upperBoundKwh
) {
}