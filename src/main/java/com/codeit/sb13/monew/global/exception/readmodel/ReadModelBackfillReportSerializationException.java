package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

/** 초기 투영 정합성 검증 결과를 checkpoint JSON으로 만들 수 없을 때 발생한다. */
public class ReadModelBackfillReportSerializationException extends ReadModelBackfillException {

    public ReadModelBackfillReportSerializationException(UUID runId, Throwable cause) {
        super(
                ApiErrorCode.READ_MODEL_BACKFILL_REPORT_SERIALIZATION_FAILED,
                Map.of("runId", runId),
                cause
        );
    }
}
