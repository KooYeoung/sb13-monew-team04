package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;

public interface UserService {

   UserCreateResponse signUp(UserCreateRequest request);

}
