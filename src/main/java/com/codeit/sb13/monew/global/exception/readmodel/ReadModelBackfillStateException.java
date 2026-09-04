package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** checkpoint 상태 전이가 허용되지 않을 때 발생한다. */
public class ReadModelBackfillStateException extends ReadModelBackfillException {

    public ReadModelBackfillStateException(UUID runId, String status, String operation) {
        super(ApiErrorCode.READ_MODEL_BACKFILL_STATE_INVALID, details(runId, status, operation));
    }

    private static Map<String, Object> details(UUID runId, String status, String operation) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("runId", runId);
        details.put("status", status);
        details.put("operation", operation);
        return details;
    }
}
