package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import java.util.Map;

/** MongoDB Read Model 조회 계약 위반 또는 문서 변환 실패의 기반 예외다. */
public abstract class ReadModelQueryException extends MonewException {

    protected ReadModelQueryException(ApiErrorCode errorCode, Map<String, Object> details) {
        super(errorCode, details);
    }

    protected ReadModelQueryException(
            ApiErrorCode errorCode,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(errorCode, details, cause);
    }
}
