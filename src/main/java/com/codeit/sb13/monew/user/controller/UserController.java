package com.codeit.sb13.monew.user.controller;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.controller.dto.UserLoginRequest;
import com.codeit.sb13.monew.user.controller.dto.UserLoginResponse;
import com.codeit.sb13.monew.user.service.UserService;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginCommand;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private static final String USER_ID_HEADER = "MoNew-Request-User-ID";

  @PostMapping
  public ResponseEntity<UserCreateResponse> signUp(
      @Valid @RequestBody UserCreateRequest request) {
    UserCreateCommand command = new UserCreateCommand(
        request.email(),
        request.nickname(),
        request.password());
    UserCreateResult result = userService.signUp(command);
    UserCreateResponse response = new UserCreateResponse(
        result.userId(),
        result.email(),
        result.nickname(),
        result.createdAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<UserLoginResponse> login(
     @Valid @RequestBody UserLoginRequest request
  ) {
    UserLoginCommand command = new UserLoginCommand(
        request.email(),
        request.password());
    UserLoginResult login = userService.login(command);

    UserLoginResponse response = new UserLoginResponse(
        login.userId(),
        login.email(),
        login.nickname());
    return ResponseEntity.status(HttpStatus.OK)
        .header(USER_ID_HEADER, login.userId().toString())
        .body(response);
  }

}
