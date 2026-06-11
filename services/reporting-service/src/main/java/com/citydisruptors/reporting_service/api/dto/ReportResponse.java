package com.citydisruptors.reporting_service.api.dto;

import com.citydisruptors.reporting_service.entity.Report;
import com.citydisruptors.reporting_service.entity.ReportFormat;
import com.citydisruptors.reporting_service.entity.ReportStatus;
import com.citydisruptors.reporting_service.entity.ReportType;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        ReportType type,
        ReportFormat format,
        ReportStatus status,
        String title,
        String contentJson,
        Instant createdAt
) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getType(),
                report.getFormat(),
                report.getStatus(),
                report.getTitle(),
                report.getContentJson(),
                report.getCreatedAt()
        );
    }
}
