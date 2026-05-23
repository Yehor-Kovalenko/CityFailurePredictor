package com.citydisruptors.incidentservice.api.dto;

import com.citydisruptors.incidentservice.entity.Coordinates;
import com.citydisruptors.incidentservice.entity.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentRequest(
        @NotBlank String incidentTitle,
        String incidentSummary,
        @NotNull Coordinates coordinates,
        @NotNull IncidentType incidentType
) {}
