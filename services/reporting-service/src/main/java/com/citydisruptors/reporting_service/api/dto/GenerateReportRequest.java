package com.citydisruptors.reporting_service.api.dto;

import com.citydisruptors.reporting_service.entity.ReportFormat;
import com.citydisruptors.reporting_service.entity.ReportType;
import jakarta.validation.constraints.NotNull;

public record GenerateReportRequest(
        @NotNull ReportType type,
        @NotNull ReportFormat format
) {
}
