package com.codeit.sb13.monew.global.exception;

import java.util.Map;

public class AdvisoryLockException extends MonewException {

    public AdvisoryLockException(String lockKey, String operation) {
        super(ApiErrorCode.ADVISORY_LOCK_FAILED, Map.of(
                "lockKey", lockKey,
                "operation", operation
        ));
    }

    public AdvisoryLockException(String lockKey, String operation, Throwable cause) {
        super(ApiErrorCode.ADVISORY_LOCK_FAILED, Map.of(
                "lockKey", lockKey,
                "operation", operation
        ), cause);
    }
}
