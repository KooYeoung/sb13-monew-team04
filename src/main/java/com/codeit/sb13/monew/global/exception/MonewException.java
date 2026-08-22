package com.codeit.sb13.monew.global.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public abstract class MonewException extends RuntimeException {
    private final ApiErrorCode apiErrorCode;
    private final Map<String, Object> details;

    protected MonewException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode.getMessage());
        this.apiErrorCode = apiErrorCode;
        this.details = getDetailsOrDefault(details);
    }

    protected MonewException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode.getMessage(), cause);
        this.apiErrorCode = apiErrorCode;
        this.details = getDetailsOrDefault(details);
    }

    private static Map<String, Object> getDetailsOrDefault(Map<String, Object> details) {
        return details == null ? Map.of() : details;
    }
}
