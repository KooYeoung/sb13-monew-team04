package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/** MongoDB 활동 조회의 사용자, cursor 또는 limit 조건이 유효하지 않을 때 발생한다. */
public class ReadModelQueryConditionInvalidException extends ReadModelQueryException {

    public ReadModelQueryConditionInvalidException(
            String field,
            Object rejectedValue,
            String reason
    ) {
        super(ApiErrorCode.INVALID_REQUEST, Map.of(
                "field", field,
                "rejectedValue", String.valueOf(rejectedValue),
                "reason", reason
        ));
    }
}
