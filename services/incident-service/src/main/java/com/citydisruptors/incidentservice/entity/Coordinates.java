package com.citydisruptors.incidentservice.entity;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;

@Embeddable
public record Coordinates(
        @NotBlank(message = "crs is required")
        String crs,

        @NotBlank(message = "x coordinate is required")
        String x,

        @NotBlank(message = "y coordinate is required")
        String y
) {
}
