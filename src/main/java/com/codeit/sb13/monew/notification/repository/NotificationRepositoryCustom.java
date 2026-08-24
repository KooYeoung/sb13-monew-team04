package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.repository.dto.NotificationFindCondition;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepositoryCustom {

    List<Notification> findUnconfirmedByUserWithCursor(NotificationFindCondition condition);

    long deleteConfirmedBefore(LocalDateTime time);
}
