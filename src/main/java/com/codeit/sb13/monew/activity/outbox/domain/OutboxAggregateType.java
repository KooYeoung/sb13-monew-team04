package com.codeit.sb13.monew.activity.outbox.domain;

/**
 * Outbox 이벤트가 가리키는 RDB 원본 도메인의 종류다.
 *
 * <p>{@link OutboxEvent}의 {@code aggregateId}와 함께 worker가 현재 상태를 다시 조회할
 * 대상을 결정하는 공통 envelope 값으로 사용한다.</p>
 */
public enum OutboxAggregateType {
    INTEREST,
    COMMENT,
    ARTICLE,
    USER
}
