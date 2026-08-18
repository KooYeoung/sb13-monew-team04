package com.codeit.sb13.monew.global.exception.user;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;

import java.util.Map;

public abstract class UserException extends MonewException {

    protected UserException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }
}
