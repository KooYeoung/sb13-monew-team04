package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

public class OutboxEventStateTransitionException extends OutboxException {

    public OutboxEventStateTransitionException(
            String currentStatus,
            String targetStatus
    ) {
        super(ApiErrorCode.OUTBOX_STATE_TRANSITION_INVALID, Map.of(
                "currentStatus", currentStatus,
                "targetStatus", targetStatus
        ));
    }
}
