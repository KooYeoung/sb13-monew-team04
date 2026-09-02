package com.codeit.sb13.monew.activity.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadSerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxPayloadSerializerTest {

    @Test
    @DisplayName("타입 payload를 JSON으로 직렬화하고 record로 다시 읽을 수 있다")
    void serializeAndDeserializeTypedPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxPayloadSerializer serializer = new OutboxPayloadSerializer(objectMapper);
        CommentOutboxPayload payload = new CommentOutboxPayload(
                UUID.randomUUID(),
                OutboxEventAction.WRITTEN
        );

        JsonNode json = serializer.serialize(payload);
        CommentOutboxPayload restored = objectMapper.treeToValue(json, CommentOutboxPayload.class);

        assertThat(restored).isEqualTo(payload);
        assertThat(json.has("eventId")).isFalse();
        assertThat(json.has("eventType")).isFalse();
        assertThat(json.has("aggregateId")).isFalse();
        assertThat(json.has("actorUserId")).isFalse();
        assertThat(json.has("occurredAt")).isFalse();
    }

    @Test
    @DisplayName("사용자 물리삭제 payload는 영향 대상 ID를 순서 유지하며 중복 제거한다")
    void hardDeletePayloadDeduplicatesImpactedIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        UserHardDeleteOutboxPayload payload = new UserHardDeleteOutboxPayload(
                OutboxEventAction.HARD_DELETED,
                List.of(first, second, first),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(payload.authoredCommentIds()).containsExactly(first, second);
    }

    @Test
    @DisplayName("payload 직렬화 실패는 OBX_002 커스텀 예외로 변환한다")
    void serializationFailureUsesCustomException() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        OutboxPayloadSerializer serializer = new OutboxPayloadSerializer(objectMapper);
        CommentOutboxPayload payload = new CommentOutboxPayload(
                UUID.randomUUID(),
                OutboxEventAction.UPDATED
        );
        IllegalArgumentException cause = new IllegalArgumentException("serialization failed");
        given(objectMapper.valueToTree(payload)).willThrow(cause);

        assertThatThrownBy(() -> serializer.serialize(payload))
                .isInstanceOfSatisfying(OutboxPayloadSerializationException.class, exception -> {
                    assertThat(exception.getApiErrorCode())
                            .isEqualTo(ApiErrorCode.OUTBOX_PAYLOAD_SERIALIZATION_FAILED);
                    assertThat(exception.getDetails())
                            .isEqualTo(Map.of("payloadType", "CommentOutboxPayload"));
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }
}
