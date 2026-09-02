package com.codeit.sb13.monew.activity.outbox.domain;

public enum OutboxEventStatus {
    PENDING,
    PROCESSED,
    FAILED,
    DEAD_LETTER
}
