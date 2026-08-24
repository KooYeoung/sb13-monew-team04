package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.QNotification;
import com.codeit.sb13.monew.notification.repository.dto.NotificationFindCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public List<Notification> findUnconfirmedByUserWithCursor(NotificationFindCondition condition) {
        QNotification notification = QNotification.notification;

        BooleanBuilder builder = new BooleanBuilder()
                .and(notification.user.id.eq(condition.userId()))
                .and(notification.confirmed.isFalse());

        if (condition.cursorId() != null && condition.after() != null) {
            builder.and(notification.createdAt.lt(condition.after())
                            .or(notification.createdAt.eq(condition.after()).and(notification.id.lt(condition.cursorId())))
            );
        }

        return queryFactory
                .selectFrom(notification)
                .where(builder)
                .orderBy(notification.createdAt.desc(), notification.id.desc())
                .limit(condition.limit())
                .fetch();
    }

    @Override
    public long deleteConfirmedBefore(LocalDateTime time) {
        QNotification notification = QNotification.notification;

        long deletedCount = queryFactory
                .delete(notification)
                .where(
                        notification.confirmed.isTrue(),
                        notification.confirmedAt.before(time)
                )
                .execute();

        entityManager.flush();
        entityManager.clear();
        return deletedCount;
    }
}
