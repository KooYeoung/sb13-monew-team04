package com.codeit.sb13.monew.activity.outbox.worker.claim;

import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import com.codeit.sb13.monew.global.exception.outbox.OutboxHeartbeatRenewException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 실행 중인 claim heartbeat의 상태와 생명주기를 worker에 노출하는 handle이다.
 *
 * <p>worker는 새로운 단계나 이벤트를 시작하기 전에 {@link #verifyHealthy()}를
 * 호출한다. handle을 닫으면 heartbeat 예약만 취소하며 아직 처리하지 못한 Outbox
 * claim은 DB lease 만료 후 다른 worker가 회수한다.</p>
 */
public final class OutboxClaimLease implements AutoCloseable {

    private final ScheduledFuture<?> heartbeat;
    private final AtomicReference<RuntimeException> failure;

    OutboxClaimLease(
            ScheduledFuture<?> heartbeat,
            AtomicReference<RuntimeException> failure
    ) {
        this.heartbeat = heartbeat;
        this.failure = failure;
    }

    /**
     * 마지막 heartbeat까지 claim을 정상적으로 유지했는지 확인한다.
     *
     * @throws OutboxHeartbeatRenewException heartbeat DB 갱신에 실패한 경우
     * @throws OutboxClaimOwnershipLostException claim 소유권을 잃은 경우
     */
    public void verifyHealthy() {
        RuntimeException heartbeatFailure = failure.get();
        if (heartbeatFailure != null) {
            throw heartbeatFailure;
        }
    }

    /**
     * 이 실행의 heartbeat 예약을 취소한다.
     *
     * <p>이미 실행 중인 heartbeat 작업은 강제로 interrupt하지 않는다.</p>
     */
    @Override
    public void close() {
        heartbeat.cancel(false);
    }
}
