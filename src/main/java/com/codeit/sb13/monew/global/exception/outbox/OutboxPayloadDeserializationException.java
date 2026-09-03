package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/**
 * worker가 저장된 JSON payload를 이벤트 타입에 맞는 record로 복원하지 못했음을
 * 나타낸다.
 */
public class OutboxPayloadDeserializationException extends OutboxException {

    /**
     * 복원 대상 타입과 Jackson 원인을 보존해 예외를 생성한다.
     *
     * @param payloadType 복원에 실패한 payload 타입 이름
     * @param cause JSON 역직렬화 실패 원인
     */
    public OutboxPayloadDeserializationException(String payloadType, Throwable cause) {
        super(
                ApiErrorCode.OUTBOX_PAYLOAD_DESERIALIZATION_FAILED,
                Map.of("payloadType", payloadType),
                cause
        );
    }
}
