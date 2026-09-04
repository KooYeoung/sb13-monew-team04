package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import java.util.List;
import java.util.UUID;

/** claim transaction에서 고정한 projection page 또는 검증 작업이다. */
public record ReadModelBackfillWork(
        ReadModelBackfillWorkType type,
        UUID runId,
        UUID claimId,
        ReadModelBackfillStage stage,
        List<InitialProjectionEvent> events,
        ProjectionSourceBatch source
) {
    public ReadModelBackfillWork {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static ReadModelBackfillWork none(UUID runId) {
        return new ReadModelBackfillWork(
                ReadModelBackfillWorkType.NONE, runId, null, null, List.of(), null);
    }

    public static ReadModelBackfillWork verification(UUID runId, UUID claimId) {
        return new ReadModelBackfillWork(
                ReadModelBackfillWorkType.VERIFY, runId, claimId, null, List.of(), null);
    }

    public boolean isEmpty() {
        return type == ReadModelBackfillWorkType.NONE;
    }
}
