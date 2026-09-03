package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 사용자 닉네임 변경 또는 논리삭제 이벤트의 payload다.
 *
 * @param action 사용자에게 발생한 동작
 */
public record UserOutboxPayload(OutboxEventAction action) implements OutboxEventPayload {
}
