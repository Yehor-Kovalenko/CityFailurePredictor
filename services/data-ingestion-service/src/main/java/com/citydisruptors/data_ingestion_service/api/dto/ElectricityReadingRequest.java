package com.citydisruptors.data_ingestion_service.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ElectricityReadingRequest(
        @NotBlank String householdId,
        @NotBlank String tariffType,
        @NotNull Instant timestamp,
        @NotNull Double kwh
) {
}
