package com.citydisruptors.incidentservice.api.dto;

import com.citydisruptors.incidentservice.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIncidentStatusRequest(
        @NotNull IncidentStatus status
) {}
