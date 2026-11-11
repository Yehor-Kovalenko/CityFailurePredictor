package com.citydisruptors.notification_service.api.controller.rest;

import com.citydisruptors.notification_service.push.PushNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/push")
public class PushSubscriptionController {

    public record PublicKeyResponse(String publicKey) {
    }

    public record SubscriptionKeys(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record SubscribeRequest(@NotBlank String endpoint, @NotNull @Valid SubscriptionKeys keys) {
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {
    }

    private final PushNotificationService service;

    public PushSubscriptionController(PushNotificationService service) {
        this.service = service;
    }

    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> publicKey() {
        if (!service.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(new PublicKeyResponse(service.publicKey()));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request) {
        if (!service.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        service.subscribe(request.endpoint(), request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        service.unsubscribe(request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
