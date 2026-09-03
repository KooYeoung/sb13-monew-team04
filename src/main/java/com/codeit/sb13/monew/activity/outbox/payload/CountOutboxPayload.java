package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 집계값을 다시 계산해야 한다는 신호를 전달하는 payload다.
 *
 * <p>과거 count 값은 저장하지 않으며 worker가 처리 시점의 RDB 값을 조회한다.</p>
 *
 * @param action 집계값 변경 동작
 */
public record CountOutboxPayload(OutboxEventAction action) implements OutboxEventPayload {
}
