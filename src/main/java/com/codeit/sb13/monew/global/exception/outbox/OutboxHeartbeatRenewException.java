package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * claim heartbeat가 DB lease를 갱신하지 못했을 때 발생한다.
 */
public class OutboxHeartbeatRenewException extends OutboxException {

    /**
     * 갱신하지 못한 claim과 원인을 보존해 예외를 생성한다.
     *
     * @param claimId 갱신하려던 claim UUID
     * @param cause DB lease 갱신 실패 원인
     */
    public OutboxHeartbeatRenewException(UUID claimId, Throwable cause) {
        super(
                ApiErrorCode.OUTBOX_HEARTBEAT_RENEW_FAILED,
                Map.of("claimId", claimId),
                cause
        );
    }
}
