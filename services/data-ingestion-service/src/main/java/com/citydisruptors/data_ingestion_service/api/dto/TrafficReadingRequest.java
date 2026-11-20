package com.citydisruptors.data_ingestion_service.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record TrafficReadingRequest(
        @NotBlank String junctionId,
        @NotNull Instant timestamp,
        @NotNull @PositiveOrZero Integer vehicles
) {
}
