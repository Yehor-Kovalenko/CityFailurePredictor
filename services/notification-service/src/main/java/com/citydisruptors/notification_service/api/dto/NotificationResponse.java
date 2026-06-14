package com.citydisruptors.notification_service.api.dto;

import com.citydisruptors.notification_service.entity.Notification;
import com.citydisruptors.notification_service.entity.NotificationSeverity;
import com.citydisruptors.notification_service.entity.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
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
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAlertId(),
                notification.getHouseholdId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getSeverity(),
                notification.getStatus(),
                notification.getRiskScore(),
                notification.getPredictedKwh(),
                notification.getTargetTimestamp(),
                notification.getCreatedAt()
        );
    }
}