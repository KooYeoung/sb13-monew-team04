package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/**
 * producer가 타입별 payload를 저장용 JSON으로 변환하지 못했음을 나타낸다.
 *
 * <p>Outbox 저장과 원본 변경이 같은 트랜잭션이므로 이 예외가 발생하면 둘 다
 * 롤백된다.</p>
 */
public class OutboxPayloadSerializationException extends OutboxException {

    /**
     * 변환 대상 타입과 Jackson 원인을 보존해 예외를 생성한다.
     *
     * @param payloadType 변환에 실패한 payload 타입 이름
     * @param cause JSON 직렬화 실패 원인
     */
    public OutboxPayloadSerializationException(String payloadType, Throwable cause) {
        super(
                ApiErrorCode.OUTBOX_PAYLOAD_SERIALIZATION_FAILED,
                Map.of("payloadType", payloadType),
                cause
        );
    }
}
