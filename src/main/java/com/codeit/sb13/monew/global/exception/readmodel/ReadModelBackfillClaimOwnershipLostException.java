package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/** 완료 또는 실패를 저장하려는 실행이 checkpoint claim 소유권을 잃었을 때 발생한다. */
public class ReadModelBackfillClaimOwnershipLostException extends ReadModelBackfillException {

    public ReadModelBackfillClaimOwnershipLostException(UUID runId, UUID claimId) {
        super(ApiErrorCode.READ_MODEL_BACKFILL_CLAIM_OWNERSHIP_LOST, Map.of(
                "runId", runId,
                "claimId", claimId
        ));
    }
}
