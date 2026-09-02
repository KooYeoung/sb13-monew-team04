package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

public class OutboxPayloadSerializationException extends OutboxException {

    public OutboxPayloadSerializationException(String payloadType, Throwable cause) {
        super(
                ApiErrorCode.OUTBOX_PAYLOAD_SERIALIZATION_FAILED,
                Map.of("payloadType", payloadType),
                cause
        );
    }
}
