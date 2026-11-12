package com.citydisruptors.notification_service.push;

import com.citydisruptors.notification_service.entity.Notification;
import com.citydisruptors.notification_service.entity.NotificationStatus;
import com.citydisruptors.notification_service.repository.NotificationRepository;
import com.citydisruptors.notification_service.service.NotificationCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final List<String> PUSH_SERVICE_DOMAINS = List.of(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "push.apple.com",
            "notify.windows.com");
    private static final int MAX_BODY_LENGTH = 300;

    private final PushSubscriptionRepository repository;
    private final NotificationRepository notificationRepository;
    private final WebPushSender sender;
    private final PushProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry registry;

    public PushNotificationService(
            PushSubscriptionRepository repository,
            NotificationRepository notificationRepository,
            WebPushSender sender,
            PushProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry registry
    ) {
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.sender = sender;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    public String publicKey() {
        return properties.publicKey();
    }

    @Transactional
    public void subscribe(String endpoint, String p256dh, String auth) {
        requireKnownPushService(endpoint);
        sender.validateKeys(p256dh, auth);

        repository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> existing.updateKeys(p256dh, auth),
                () -> repository.save(new PushSubscription(endpoint, p256dh, auth)));
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }

    @Async
    @TransactionalEventListener
    public void onNotificationCreated(NotificationCreatedEvent event) {
        if (!isEnabled()) {
            return;
        }

        Notification notification = event.notification();
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(Map.of(
                    "title", notification.getTitle(),
                    "body", truncate(notification.getMessage()),
                    "severity", notification.getSeverity().name(),
                    "alertId", notification.getAlertId(),
                    "unreadCount", notificationRepository.countByStatus(NotificationStatus.UNREAD)));
        } catch (Exception e) {
            log.error("Could not serialize push payload for notification {}", notification.getId(), e);
            return;
        }

        WebPushSender.Urgency urgency = switch (notification.getSeverity()) {
            case CRITICAL, HIGH -> WebPushSender.Urgency.HIGH;
            case MEDIUM -> WebPushSender.Urgency.NORMAL;
            case LOW -> WebPushSender.Urgency.LOW;
        };

        for (PushSubscription subscription : repository.findAll()) {
            deliver(subscription, payload, urgency);
        }
    }

    private void deliver(PushSubscription subscription, byte[] payload, WebPushSender.Urgency urgency) {
        String result;
        try {
            int status = sender.send(subscription, payload, urgency);
            if (status == 404 || status == 410) {
                repository.delete(subscription);
                result = "expired";
            } else if (status >= 200 && status < 300) {
                result = "delivered";
            } else {
                log.warn("Push service rejected a message with status {}", status);
                result = "failed";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = "failed";
        } catch (Exception e) {
            log.warn("Push delivery failed: {}", e.getMessage());
            result = "failed";
        }
        registry.counter("notifications.push.deliveries.total", "result", result).increment();
    }

    private static void requireKnownPushService(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push endpoint", e);
        }

        String host = uri.getHost();
        boolean known = "https".equals(uri.getScheme())
                && uri.getRawUserInfo() == null
                && host != null
                && PUSH_SERVICE_DOMAINS.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        if (!known) {
            throw new IllegalArgumentException("Push endpoint must be an https URL of a known push service");
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_BODY_LENGTH ? text : text.substring(0, MAX_BODY_LENGTH - 1) + "…";
    }
}
