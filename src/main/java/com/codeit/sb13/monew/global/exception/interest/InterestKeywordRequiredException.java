package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class InterestKeywordRequiredException extends InterestException {

    public InterestKeywordRequiredException(UUID interestId) {
        super(ApiErrorCode.INTEREST_KEYWORD_REQUIRED, Map.of("interestId", interestId));
    }
}
