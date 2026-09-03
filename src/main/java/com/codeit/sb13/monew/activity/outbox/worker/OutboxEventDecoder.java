package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.UserOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.service.OutboxPayloadSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 이벤트 종류와 JSON payload를 타입이 지정된 worker 이벤트로 변환한다.
 *
 * <p>이벤트 종류별 payload 매핑을 한곳에서 관리해 이후 source 조회와 projection
 * 처리에서 타입 추론을 반복하지 않게 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxEventDecoder {

    private final OutboxPayloadSerializer payloadSerializer;

    /**
     * 저장된 이벤트 envelope를 유지하면서 JSON payload를 알맞은 record로 복원한다.
     *
     * @param event claim된 Outbox JPA 엔티티
     * @return worker 처리에 사용할 불변 이벤트
     * @throws com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadDeserializationException payload JSON이 이벤트 타입과 호환되지 않는 경우
     */
    public DecodedOutboxEvent decode(OutboxEvent event) {
        OutboxEventPayload payload = switch (event.getEventType()) {
            case INTEREST_SUBSCRIBED, INTEREST_UNSUBSCRIBED, INTEREST_UPDATED,
                    INTEREST_HARD_DELETED -> deserialize(event, InterestOutboxPayload.class);
            case COMMENT_WRITTEN, COMMENT_UPDATED, COMMENT_SOFT_DELETED,
                    COMMENT_HARD_DELETED -> deserialize(event, CommentOutboxPayload.class);
            case COMMENT_LIKED, COMMENT_LIKE_CANCELED ->
                    deserialize(event, CommentLikeOutboxPayload.class);
            case ARTICLE_VIEWED, ARTICLE_SOFT_DELETED, ARTICLE_HARD_DELETED ->
                    deserialize(event, ArticleOutboxPayload.class);
            case USER_NICKNAME_UPDATED, USER_SOFT_DELETED ->
                    deserialize(event, UserOutboxPayload.class);
            case USER_HARD_DELETED -> deserialize(event, UserHardDeleteOutboxPayload.class);
            case INTEREST_SUBSCRIBER_COUNT_CHANGED, COMMENT_LIKE_CHANGED,
                    ARTICLE_VIEW_COUNT_CHANGED, ARTICLE_COMMENT_COUNT_CHANGED ->
                    deserialize(event, CountOutboxPayload.class);
        };

        return new DecodedOutboxEvent(
                event.getId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getActorUserId(),
                payload,
                event.getProjectionVersion(),
                event.getRetryCount(),
                event.getOccurredAt(),
                event.getCreatedAt()
        );
    }

    private <T extends OutboxEventPayload> T deserialize(OutboxEvent event, Class<T> payloadType) {
        return payloadSerializer.deserialize(event.getPayloadJson(), payloadType);
    }
}
