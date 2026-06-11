package com.citydisruptors.reporting_service.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Report() {
    }

    public Report(UUID id, ReportType type, ReportFormat format, ReportStatus status,
                  String title, String contentJson, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.format = format;
        this.status = status;
        this.title = title;
        this.contentJson = contentJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public ReportType getType() {
        return type;
    }

    public ReportFormat getFormat() {
        return format;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getContentJson() {
        return contentJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
