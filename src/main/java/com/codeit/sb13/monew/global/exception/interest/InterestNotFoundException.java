package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class InterestNotFoundException extends InterestException {

    public InterestNotFoundException(UUID interestId) {
        super(ApiErrorCode.INTEREST_NOT_FOUND, Map.of("interestId", interestId));
    }
}
