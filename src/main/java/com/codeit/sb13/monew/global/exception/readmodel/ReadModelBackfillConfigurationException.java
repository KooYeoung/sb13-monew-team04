package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/** 초기 투영 설정이 안전한 batch 실행 조건을 만족하지 않을 때 발생한다. */
public class ReadModelBackfillConfigurationException extends ReadModelBackfillException {

    public ReadModelBackfillConfigurationException(
            String property,
            Object rejectedValue,
            String reason
    ) {
        super(ApiErrorCode.READ_MODEL_BACKFILL_CONFIGURATION_INVALID, Map.of(
                "property", property,
                "rejectedValue", String.valueOf(rejectedValue),
                "reason", reason
        ));
    }
}
