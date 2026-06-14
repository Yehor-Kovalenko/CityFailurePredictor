package com.citydisruptors.notification_service.api.dto;

import java.util.List;
import java.util.UUID;

public record MarkNotificationReadRequest(
        List<UUID> notificationIds
) {
}