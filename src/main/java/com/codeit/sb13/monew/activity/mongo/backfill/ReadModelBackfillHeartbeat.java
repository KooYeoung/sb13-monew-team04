package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillClaimOwnershipLostException;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillHeartbeatException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 처리 중인 초기 투영 batch의 checkpoint claim lease를 주기적으로 연장한다. */
@Component
public class ReadModelBackfillHeartbeat {

    private final ReadModelBackfillClaimService claimService;
    private final ReadModelBackfillProperties properties;

    private final ScheduledExecutorService executor;

    public ReadModelBackfillHeartbeat(
            ReadModelBackfillClaimService claimService,
            ReadModelBackfillProperties properties,
            @Qualifier("readModelBackfillHeartbeatExecutor")
            ScheduledExecutorService executor
    ) {
        this.claimService = claimService;
        this.properties = properties;
        this.executor = executor;
    }

    public ReadModelBackfillLease start(UUID runId, UUID claimId) {
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        long interval = properties.heartbeatInterval().toMillis();
        try {
            ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                    () -> renew(runId, claimId, failure),
                    interval,
                    interval,
                    TimeUnit.MILLISECONDS
            );
            return new ReadModelBackfillLease(future, failure);
        } catch (RuntimeException e) {
            throw new ReadModelBackfillHeartbeatException(runId, claimId, e);
        }
    }

    private void renew(
            UUID runId,
            UUID claimId,
            AtomicReference<RuntimeException> failure
    ) {
        if (failure.get() != null) {
            return;
        }
        try {
            int renewed = claimService.renew(runId, claimId, properties.claimLease());
            if (renewed == 0) {
                failure.compareAndSet(
                        null,
                        new ReadModelBackfillClaimOwnershipLostException(runId, claimId)
                );
            }
        } catch (RuntimeException e) {
            failure.compareAndSet(
                    null,
                    new ReadModelBackfillHeartbeatException(runId, claimId, e)
            );
        }
    }
}
