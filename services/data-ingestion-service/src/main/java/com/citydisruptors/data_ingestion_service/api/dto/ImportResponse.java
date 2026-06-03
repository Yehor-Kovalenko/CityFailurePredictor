package com.citydisruptors.data_ingestion_service.api.dto;

public record ImportResponse(
        String fileName,
        long importedRows,
        long skippedRows,
        long limit
) {
}
