package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;

public interface UserService {

  void signUp(UserCreateRequest request);

}
