package com.codeit.sb13.monew.activity.mongo.backfill;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/** 초기 투영 heartbeat 상태를 worker에 전달하는 closeable handle이다. */
public final class ReadModelBackfillLease implements AutoCloseable {

    private final ScheduledFuture<?> heartbeat;
    private final AtomicReference<RuntimeException> failure;

    ReadModelBackfillLease(
            ScheduledFuture<?> heartbeat,
            AtomicReference<RuntimeException> failure
    ) {
        this.heartbeat = heartbeat;
        this.failure = failure;
    }

    public void verifyHealthy() {
        RuntimeException value = failure.get();
        if (value != null) {
            throw value;
        }
    }

    @Override
    public void close() {
        heartbeat.cancel(false);
    }
}
