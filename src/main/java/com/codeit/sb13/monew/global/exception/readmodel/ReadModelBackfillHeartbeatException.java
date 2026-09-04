package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/** 초기 투영 claim heartbeat 시작 또는 갱신에 실패했을 때 발생한다. */
public class ReadModelBackfillHeartbeatException extends ReadModelBackfillException {

    public ReadModelBackfillHeartbeatException(UUID runId, UUID claimId, Throwable cause) {
        super(ApiErrorCode.READ_MODEL_BACKFILL_HEARTBEAT_FAILED, Map.of(
                "runId", runId,
                "claimId", claimId
        ), cause);
    }
}
