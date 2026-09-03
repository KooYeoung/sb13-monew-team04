package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * claim heartbeat 작업을 scheduler에 등록하지 못했을 때 발생한다.
 */
public class OutboxHeartbeatStartException extends OutboxException {

    /**
     * 시작하지 못한 claim과 원인을 보존해 예외를 생성한다.
     *
     * @param claimId heartbeat를 시작하려던 claim UUID
     * @param cause scheduler 등록 실패 원인
     */
    public OutboxHeartbeatStartException(UUID claimId, Throwable cause) {
        super(
                ApiErrorCode.OUTBOX_HEARTBEAT_START_FAILED,
                Map.of("claimId", claimId),
                cause
        );
    }
}
