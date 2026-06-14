package com.citydisruptors.notification_service.repository;

import com.citydisruptors.notification_service.entity.Notification;
import com.citydisruptors.notification_service.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByAlertId(String alertId);

    List<Notification> findAllByOrderByCreatedAtDesc();

    List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

    long countByStatus(NotificationStatus status);
}