package com.citydisruptors.notification_service.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_status_created_at", columnList = "status, created_at DESC"),
                @Index(name = "idx_notifications_household_id", columnList = "household_id"),
                @Index(name = "idx_notifications_alert_id", columnList = "alert_id", unique = true)
        }
)
public class Notification {

    @Id
    private UUID id;

    @Column(name = "alert_id", nullable = false, unique = true)
    private String alertId;

    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "predicted_kwh")
    private Double predictedKwh;

    @Column(name = "target_timestamp")
    private Instant targetTimestamp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            UUID id,
            String alertId,
            String householdId,
            String title,
            String message,
            NotificationSeverity severity,
            NotificationStatus status,
            Double riskScore,
            Double predictedKwh,
            Instant targetTimestamp,
            Instant createdAt
    ) {
        this.id = id;
        this.alertId = alertId;
        this.householdId = householdId;
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.status = status;
        this.riskScore = riskScore;
        this.predictedKwh = predictedKwh;
        this.targetTimestamp = targetTimestamp;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getHouseholdId() {
        return householdId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public Double getPredictedKwh() {
        return predictedKwh;
    }

    public Instant getTargetTimestamp() {
        return targetTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markAsRead() {
        this.status = NotificationStatus.READ;
    }
}