package com.citydisruptors.incidentservice.api.dto;

import com.citydisruptors.incidentservice.entity.Coordinates;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String incidentTitle,
        String incidentSummary,
        Coordinates coordinates,
        IncidentType incidentType,
        IncidentStatus status,
        Instant timestamp,
        Instant lastUpdated
) {}
