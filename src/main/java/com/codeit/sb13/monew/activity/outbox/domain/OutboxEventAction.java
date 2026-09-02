package com.codeit.sb13.monew.activity.outbox.domain;

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
