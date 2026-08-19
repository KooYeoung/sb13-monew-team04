package com.codeit.sb13.monew.user.exception;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.user.UserException;
import java.util.Map;

public class DuplicateEmailException extends UserException {


  public DuplicateEmailException(String email) {
    super(ApiErrorCode.DUPLICATE_EMAIL, Map.of("email", email));

  }
}
