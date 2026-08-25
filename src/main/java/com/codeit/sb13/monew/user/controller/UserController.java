package com.codeit.sb13.monew.user.controller;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.controller.dto.UserLoginRequest;
import com.codeit.sb13.monew.user.controller.dto.UserLoginResponse;
import com.codeit.sb13.monew.user.controller.dto.UserNicknameUpdateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserUpdateNicknameResponse;
import com.codeit.sb13.monew.user.service.UserService;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginCommand;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameCommand;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameResult;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.codeit.sb13.monew.global.MonewHttpHeaders;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

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
        .header(MonewHttpHeaders.REQUEST_USER_ID, login.userId().toString())
        .body(response);
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<UserUpdateNicknameResponse> updateNickname(
      @PathVariable("userId") UUID userId,
      @Valid @RequestBody UserNicknameUpdateRequest request
  ) {
    UserUpdateNicknameCommand command = new UserUpdateNicknameCommand(
        userId,
        request.nickname()
    );
    UserUpdateNicknameResult result = userService.updateNickname(command);
    UserUpdateNicknameResponse response = new UserUpdateNicknameResponse
        (result.userId(),
            result.nickname(),
            result.updatedAt());

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> deleteUser(@PathVariable("userId") UUID userId) {
    userService.deleteUser(userId);
    return ResponseEntity.status(HttpStatus.OK).build();
  }

  @DeleteMapping("/{userId}/hard")
  public ResponseEntity<Void> hardDeleteUser(@PathVariable("userId") UUID userId) {
    userService.hardDeleteUser(userId);
    return ResponseEntity.status(HttpStatus.OK).build();
  }


}
