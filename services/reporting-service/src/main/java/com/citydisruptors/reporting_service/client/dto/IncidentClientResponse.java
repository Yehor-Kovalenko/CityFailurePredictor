package com.citydisruptors.reporting_service.client.dto;

import com.citydisruptors.reporting_service.entity.Coordinates;
import com.citydisruptors.reporting_service.entity.IncidentStatus;
import com.citydisruptors.reporting_service.entity.IncidentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentClientResponse(
        UUID id,
        String incidentTitle,
        String incidentSummary,
        Coordinates coordinates,
        IncidentType incidentType,
        IncidentStatus status,
        Instant timestamp,
        Instant lastUpdated
) {
}
