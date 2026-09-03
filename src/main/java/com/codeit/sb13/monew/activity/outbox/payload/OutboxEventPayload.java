package com.codeit.sb13.monew.activity.outbox.payload;

/**
 * Outbox 이벤트가 공통 envelope 외에 보관하는 타입별 payload 계약이다.
 *
 * <p>허용된 record만 구현할 수 있으며
 * {@link com.codeit.sb13.monew.activity.outbox.service.OutboxPayloadSerializer}가
 * JSON으로 변환한다. 변경 가능한 표시값의 최종 snapshot이 아니라 worker가 현재
 * RDB 상태를 재조회할 때 필요한 식별자와 동작을 전달한다.</p>
 */
public sealed interface OutboxEventPayload permits
        ArticleOutboxPayload,
        CommentLikeOutboxPayload,
        CommentOutboxPayload,
        CountOutboxPayload,
        InterestOutboxPayload,
        UserHardDeleteOutboxPayload,
        UserOutboxPayload {
}
