package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/** 전역 projection 버전을 발급할 수 없을 때 발생한다. */
public class OutboxProjectionVersionAllocationException extends OutboxException {

    public OutboxProjectionVersionAllocationException(Throwable cause) {
        super(
                ApiErrorCode.OUTBOX_PROJECTION_VERSION_ALLOCATION_FAILED,
                Map.of("reason", cause.getClass().getSimpleName()),
                cause
        );
    }

    public OutboxProjectionVersionAllocationException() {
        super(ApiErrorCode.OUTBOX_PROJECTION_VERSION_ALLOCATION_FAILED, Map.of());
    }
}
