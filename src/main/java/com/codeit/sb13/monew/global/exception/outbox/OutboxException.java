package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import java.util.Map;

public abstract class OutboxException extends MonewException {

    protected OutboxException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    protected OutboxException(
            ApiErrorCode apiErrorCode,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(apiErrorCode, details, cause);
    }
}
