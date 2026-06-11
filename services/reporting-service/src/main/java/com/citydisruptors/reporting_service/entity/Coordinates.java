package com.citydisruptors.reporting_service.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public record Coordinates(
        String crs,
        String x,
        String y
) {
}
