package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/**
 * 종결된 Outbox 이벤트를 다시 변경하는 등 허용되지 않은 상태 전이 요청을 나타낸다.
 */
public class OutboxEventStateTransitionException extends OutboxException {

    /**
     * 거부된 현재 상태와 목표 상태를 담아 예외를 생성한다.
     *
     * @param currentStatus 변경 전 이벤트 상태
     * @param targetStatus 요청한 목표 상태
     */
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
