package com.citydisruptors.notification_service.service;

import com.citydisruptors.notification_service.entity.Notification;

public record NotificationCreatedEvent(Notification notification) {
}
