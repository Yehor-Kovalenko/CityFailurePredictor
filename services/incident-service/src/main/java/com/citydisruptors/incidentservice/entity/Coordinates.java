package com.citydisruptors.incidentservice.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public record Coordinates(
        String crs,
        String x,
        String y
) {}
