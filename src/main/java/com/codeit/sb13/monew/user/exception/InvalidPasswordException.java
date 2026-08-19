package com.codeit.sb13.monew.user.exception;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.user.UserException;
import java.util.Map;

public class InvalidPasswordException extends UserException {

  public InvalidPasswordException(String email) {
    super(ApiErrorCode.LOGIN_FAILED, Map.of("email", email));
  }
}
