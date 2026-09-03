package com.codeit.sb13.monew.activity.outbox.service;

import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadDeserializationException;
import com.codeit.sb13.monew.global.exception.outbox.OutboxPayloadSerializationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 타입이 지정된 Outbox payload와 저장용 JSON tree 사이의 변환을 담당한다.
 *
 * <p>Jackson 예외가 저장 또는 worker 경계 밖으로 직접 노출되지 않도록 Outbox
 * 전용 예외로 변환한다.</p>
 */
@Component
public class OutboxPayloadSerializer {

    private final ObjectMapper objectMapper;

    public OutboxPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * payload record를 PostgreSQL JSONB 컬럼에 저장할 JSON tree로 변환한다.
     *
     * @param payload 직렬화할 타입별 payload
     * @return payload와 동일한 내용을 가진 JSON tree
     * @throws OutboxPayloadSerializationException payload를 변환할 수 없는 경우
     */
    public JsonNode serialize(OutboxEventPayload payload) {
        try {
            return objectMapper.valueToTree(payload);
        } catch (JacksonException e) {
            throw new OutboxPayloadSerializationException(payload.getClass().getSimpleName(), e);
        }
    }

    /**
     * 저장된 JSON tree를 이벤트 타입에 대응하는 payload record로 복원한다.
     *
     * @param payload 역직렬화할 JSON tree
     * @param payloadType 복원할 payload record 타입
     * @param <T> 허용된 Outbox payload 타입
     * @return 지정한 타입으로 복원된 payload
     * @throws OutboxPayloadDeserializationException JSON 구조가 타입과 호환되지 않는 경우
     */
    public <T extends OutboxEventPayload> T deserialize(JsonNode payload, Class<T> payloadType) {
        try {
            return objectMapper.treeToValue(payload, payloadType);
        } catch (JacksonException e) {
            throw new OutboxPayloadDeserializationException(payloadType.getSimpleName(), e);
        }
    }
}
