package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.activity.mongo.backfill.ReadModelBackfillStage;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 초기 투영 또는 검증 page가 유효한 다음 cursor를 제공하지 못할 때 발생한다. */
public class ReadModelBackfillProgressException extends ReadModelBackfillException {

    public ReadModelBackfillProgressException(
            ReadModelBackfillStage stage,
            UUID currentCursor,
            UUID nextCursor,
            String reason
    ) {
        super(
                ApiErrorCode.READ_MODEL_BACKFILL_PROGRESS_INVALID,
                details(stage, currentCursor, nextCursor, reason)
        );
    }

    private static Map<String, Object> details(
            ReadModelBackfillStage stage,
            UUID currentCursor,
            UUID nextCursor,
            String reason
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stage", stage);
        details.put("currentCursor", currentCursor);
        details.put("nextCursor", nextCursor);
        details.put("reason", reason);
        return details;
    }
}
