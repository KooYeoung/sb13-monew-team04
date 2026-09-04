package com.codeit.sb13.monew.activity.mongo.backfill;

import java.util.UUID;

/** 한 scheduler tick에서 수행한 초기 투영 또는 검증 결과다. */
public record ReadModelBackfillResult(
        UUID runId,
        ReadModelBackfillWorkType workType,
        int selected,
        int processed,
        boolean failed,
        Boolean verificationMatched
) {

    public static ReadModelBackfillResult empty(UUID runId) {
        return new ReadModelBackfillResult(
                runId, ReadModelBackfillWorkType.NONE, 0, 0, false, null);
    }

    public static ReadModelBackfillResult projection(
            UUID runId,
            int selected,
            int processed,
            boolean failed
    ) {
        return new ReadModelBackfillResult(
                runId, ReadModelBackfillWorkType.PROJECT,
                selected, processed, failed, null);
    }

    public static ReadModelBackfillResult verification(
            UUID runId,
            boolean matched,
            boolean failed
    ) {
        return new ReadModelBackfillResult(
                runId, ReadModelBackfillWorkType.VERIFY,
                0, 0, failed, failed ? null : matched);
    }
}
