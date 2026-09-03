package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 관심사 변경 또는 구독 관계 이벤트의 payload다.
 *
 * @param action 관심사나 구독 관계에 발생한 동작
 */
public record InterestOutboxPayload(OutboxEventAction action) implements OutboxEventPayload {
}
