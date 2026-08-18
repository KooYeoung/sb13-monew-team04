package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;

public interface UserService {

   UserCreateResult signUp(UserCreateCommand command);

}
