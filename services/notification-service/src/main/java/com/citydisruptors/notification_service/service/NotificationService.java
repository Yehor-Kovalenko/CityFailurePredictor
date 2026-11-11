package com.citydisruptors.notification_service.service;

import com.citydisruptors.notification_service.config.kafka.event.AlertCreatedEvent;
import com.citydisruptors.notification_service.entity.Notification;
import com.citydisruptors.notification_service.entity.NotificationSeverity;
import com.citydisruptors.notification_service.entity.NotificationStatus;
import com.citydisruptors.notification_service.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Counter notificationsCreated;
    private final Counter duplicateAlertsSkipped;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository repository,
            MeterRegistry registry,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.notificationsCreated = registry.counter("notifications.created.total");
        this.duplicateAlertsSkipped = registry.counter("notifications.duplicates.skipped.total");
    }

    @Transactional
    public void createFromAlert(AlertCreatedEvent event) {
        if (repository.findByAlertId(event.alertId()).isPresent()) {
            duplicateAlertsSkipped.increment();
            return;
        }

        Notification notification = new Notification(
                UUID.randomUUID(),
                event.alertId(),
                event.householdId(),
                buildTitle(event),
                event.message(),
                parseSeverity(event.severity()),
                NotificationStatus.UNREAD,
                event.riskScore(),
                event.predictedKwh(),
                event.targetTimestamp(),
                event.createdAt() != null ? event.createdAt() : Instant.now()
        );

        repository.save(notification);
        notificationsCreated.increment();
        eventPublisher.publishEvent(new NotificationCreatedEvent(notification));
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(NotificationStatus status) {
        if (status == null) {
            return repository.findAllByOrderByCreatedAtDesc();
        }

        return repository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public Notification getNotification(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
    }

    @Transactional
    public Notification markAsRead(UUID id) {
        Notification notification = getNotification(id);
        notification.markAsRead();
        return notification;
    }

    @Transactional
    public void markManyAsRead(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        repository.findAllById(ids).forEach(Notification::markAsRead);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        return Map.of(
                "total", repository.count(),
                "unread", repository.countByStatus(NotificationStatus.UNREAD),
                "read", repository.countByStatus(NotificationStatus.READ)
        );
    }

    private String buildTitle(AlertCreatedEvent event) {
        return switch (parseSeverity(event.severity())) {
            case CRITICAL -> "Critical energy consumption alert";
            case HIGH -> "High energy consumption alert";
            case MEDIUM -> "Medium energy consumption alert";
            case LOW -> "Low energy consumption alert";
        };
    }

    private NotificationSeverity parseSeverity(String rawSeverity) {
        if (rawSeverity == null || rawSeverity.isBlank()) {
            return NotificationSeverity.LOW;
        }

        return NotificationSeverity.valueOf(rawSeverity.toUpperCase());
    }
}