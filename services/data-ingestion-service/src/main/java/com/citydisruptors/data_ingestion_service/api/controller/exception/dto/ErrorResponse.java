package com.citydisruptors.data_ingestion_service.api.controller.exception.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {
}
