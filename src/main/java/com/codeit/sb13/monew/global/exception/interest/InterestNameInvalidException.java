package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Collections;

public class InterestNameInvalidException extends InterestException {

    public InterestNameInvalidException(String name) {
        super(ApiErrorCode.INTEREST_NAME_INVALID, Collections.singletonMap("name", name));
    }
}
