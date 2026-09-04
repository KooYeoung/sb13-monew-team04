package com.codeit.sb13.monew.activity.outbox.worker.claim;

import com.codeit.sb13.monew.activity.outbox.worker.config.OutboxWorkerProperties;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import com.codeit.sb13.monew.global.exception.outbox.OutboxHeartbeatRenewException;
import com.codeit.sb13.monew.global.exception.outbox.OutboxHeartbeatStartException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 처리 중인 batch의 claim lease가 만료되지 않도록 주기적으로 연장한다.
 *
 * <p>전용 scheduler thread에서 {@link OutboxClaimService#renew(UUID, java.time.Duration)}를
 * 호출한다. 갱신 대상이 없거나 DB 갱신이 실패하면 예외를 lease handle에 기록하고,
 * worker가 다음 처리 경계에서 이를 감지해 중단하도록 한다.</p>
 */
@Component
public class OutboxClaimHeartbeat {

    private final OutboxClaimService claimService;
    private final OutboxWorkerProperties properties;

    private final ScheduledExecutorService heartbeatExecutor;

    public OutboxClaimHeartbeat(
            OutboxClaimService claimService,
            OutboxWorkerProperties properties,
            @Qualifier("outboxClaimHeartbeatExecutor")
            ScheduledExecutorService heartbeatExecutor
    ) {
        this.claimService = claimService;
        this.properties = properties;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    /**
     * 지정한 batch claim의 주기적 lease 연장을 시작한다.
     *
     * @param claimId 연장할 batch 실행 UUID
     * @return 상태 확인과 heartbeat 취소에 사용할 lease handle
     * @throws OutboxHeartbeatStartException heartbeat 작업을 scheduler에 등록하지 못한 경우
     */
    public OutboxClaimLease start(UUID claimId) {
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        long intervalMillis = properties.heartbeatInterval().toMillis();
        try {
            ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleWithFixedDelay(
                    () -> renew(claimId, failure),
                    intervalMillis,
                    intervalMillis,
                    TimeUnit.MILLISECONDS
            );
            return new OutboxClaimLease(heartbeat, failure);
        } catch (RuntimeException e) {
            throw new OutboxHeartbeatStartException(claimId, e);
        }
    }

    private void renew(UUID claimId, AtomicReference<RuntimeException> failure) {
        if (failure.get() != null) {
            return;
        }
        try {
            int renewed = claimService.renew(claimId, properties.claimLease());
            if (renewed == 0) {
                failure.compareAndSet(
                        null,
                        new OutboxClaimOwnershipLostException(null, claimId)
                );
            }
        } catch (RuntimeException e) {
            failure.compareAndSet(null, new OutboxHeartbeatRenewException(claimId, e));
        }
    }
}
