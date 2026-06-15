package com.citydisruptors.notification_service.config.kafka;

import com.citydisruptors.notification_service.config.kafka.event.AlertCreatedEvent;
import com.citydisruptors.notification_service.service.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertCreatedConsumer {

    private final NotificationService notificationService;
    private final Counter consumed;
    private final Counter failed;

    public AlertCreatedConsumer(NotificationService notificationService, MeterRegistry registry) {
        this.notificationService = notificationService;
        this.consumed = registry.counter("notifications.alerts.consumed.total");
        this.failed = registry.counter("notifications.alerts.failed.total");
    }

    @KafkaListener(
            topics = "${app.kafka.topics.alerts-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "alertKafkaListenerContainerFactory"
    )
    public void consume(AlertCreatedEvent event) {
        try {
            notificationService.createFromAlert(event);
            consumed.increment();
        } catch (Exception ex) {
            failed.increment();
            throw ex;
        }
    }
}