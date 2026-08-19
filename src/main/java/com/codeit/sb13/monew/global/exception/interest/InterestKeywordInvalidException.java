package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Collections;

public class InterestKeywordInvalidException extends InterestException {

    public InterestKeywordInvalidException(String keyword) {
        super(ApiErrorCode.INTEREST_KEYWORD_INVALID, Collections.singletonMap("keyword", keyword));
    }
}
