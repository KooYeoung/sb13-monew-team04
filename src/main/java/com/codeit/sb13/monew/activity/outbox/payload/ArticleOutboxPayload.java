package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 기사 변경 또는 조회 활동 이벤트의 payload다.
 *
 * @param action 기사에 발생한 동작
 */
public record ArticleOutboxPayload(OutboxEventAction action) implements OutboxEventPayload {
}
