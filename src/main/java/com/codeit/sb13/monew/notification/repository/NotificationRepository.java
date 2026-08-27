package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

    List<Notification> findByUser_IdAndConfirmedFalse(UUID userId);

    long countByUser_IdAndConfirmedFalse(UUID userId);

    void deleteByUser_Id(UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.confirmed = true,
            n.confirmedAt = :now
        WHERE n.user.id = :userId
        AND n.confirmed = false
        AND n.id IN :ids
    """)
    int confirmAllByUserId(@Param("userId") UUID userId, @Param("ids") List<UUID> ids, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.confirmed = true AND n.confirmedAt < :time")
    int deleteConfirmedBefore(@Param("time") LocalDateTime time);
}