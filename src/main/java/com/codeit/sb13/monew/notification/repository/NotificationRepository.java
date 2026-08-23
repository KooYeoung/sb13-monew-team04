package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

    List<Notification> findByUser_IdAndConfirmedFalse(UUID userId);

    long countByUser_IdAndConfirmedFalse(UUID userId);

}
