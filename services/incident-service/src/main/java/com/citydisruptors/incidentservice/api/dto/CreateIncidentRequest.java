package com.citydisruptors.incidentservice.api.dto;

import com.citydisruptors.incidentservice.entity.Coordinates;
import com.citydisruptors.incidentservice.entity.IncidentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentRequest(
        @NotBlank(message = "incidentTitle is required")
        String incidentTitle,

        @NotBlank(message = "incidentSummary is required")
        String incidentSummary,

        @NotNull(message = "coordinates are required")
        @Valid
        Coordinates coordinates,

        @NotNull(message = "incidentType is required")
        IncidentType incidentType
) {
}
