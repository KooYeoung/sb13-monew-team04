package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;

public interface UserService {

   UserCreateResponse signUp(UserCreateCommand command);

}
