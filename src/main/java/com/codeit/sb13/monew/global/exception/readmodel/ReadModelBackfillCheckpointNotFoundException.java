package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/** 초기화 직후에도 실행 checkpoint를 조회할 수 없을 때 발생한다. */
public class ReadModelBackfillCheckpointNotFoundException extends ReadModelBackfillException {

    public ReadModelBackfillCheckpointNotFoundException(UUID runId) {
        super(
                ApiErrorCode.READ_MODEL_BACKFILL_CHECKPOINT_NOT_FOUND,
                Map.of("runId", runId)
        );
    }
}
