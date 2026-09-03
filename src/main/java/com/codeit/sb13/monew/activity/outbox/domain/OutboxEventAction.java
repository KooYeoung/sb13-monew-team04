package com.codeit.sb13.monew.activity.outbox.domain;

/**
 * 타입별 Outbox payload에 기록되는 도메인 동작이다.
 *
 * <p>worker는 이 값을 과거 상태의 snapshot으로 신뢰하지 않고, 처리 시점의 RDB
 * 현재 상태를 재조회해 MongoDB projection의 최종 상태를 계산한다.</p>
 */
public enum OutboxEventAction {
    SUBSCRIBED,
    UNSUBSCRIBED,
    WRITTEN,
    LIKED,
    LIKE_CANCELED,
    VIEWED,
    TOUCHED,
    UPDATED,
    SOFT_DELETED,
    HARD_DELETED,
    COUNT_CHANGED
}
