package com.codeit.sb13.monew.user.exception;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.user.UserException;
import java.util.Map;
import java.util.UUID;

public class AlreadyDeletedUserException extends UserException {

  public AlreadyDeletedUserException(UUID userId) {
    super(ApiErrorCode.USER_ALREADY_DELETED, Map.of("userId", userId));
  }
}
