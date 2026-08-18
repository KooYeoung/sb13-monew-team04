package com.codeit.sb13.monew.global.exception.user;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class UserNotFoundException extends UserException {
    public UserNotFoundException(UUID userId) {
        super(ApiErrorCode.USER_NOT_FOUND, Map.of("userId", userId));
    }
}
