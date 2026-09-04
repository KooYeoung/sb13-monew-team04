package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import java.util.Map;

/** Read Model 초기 투영과 정합성 검증에서 발생하는 애플리케이션 예외의 기반 타입이다. */
public abstract class ReadModelBackfillException extends MonewException {

    protected ReadModelBackfillException(
            ApiErrorCode errorCode,
            Map<String, Object> details
    ) {
        super(errorCode, details);
    }

    protected ReadModelBackfillException(
            ApiErrorCode errorCode,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(errorCode, details, cause);
    }
}
