package com.citydisruptors.notification_service.api.controller.rest;

import com.citydisruptors.notification_service.api.dto.MarkNotificationReadRequest;
import com.citydisruptors.notification_service.api.dto.NotificationResponse;
import com.citydisruptors.notification_service.entity.NotificationStatus;
import com.citydisruptors.notification_service.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationRESTController {

    private final NotificationService service;

    public NotificationRESTController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/test")
    public String test() {
        return "NOTIFICATION SERVICE OK";
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(
            @RequestParam(required = false) NotificationStatus status
    ) {
        return service.getNotifications(status)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotification(@PathVariable UUID id) {
        return NotificationResponse.from(service.getNotification(id));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable UUID id) {
        return NotificationResponse.from(service.markAsRead(id));
    }

    @PatchMapping("/read")
    public void markManyAsRead(@RequestBody MarkNotificationReadRequest request) {
        service.markManyAsRead(request.notificationIds());
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return service.getSummary();
    }
}