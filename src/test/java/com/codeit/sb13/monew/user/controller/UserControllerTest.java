package com.codeit.sb13.monew.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.service.UserService;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  UserService userService;

  @Test
  @DisplayName("정상 요청 시 201과 사용자 정보를 반환한다")
  void 정상_요청시_201을_반환한다() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest(
        "email@email.com",
        "닉네임",
        "PassWord123!"
    );

    UserCreateResult result = new UserCreateResult(
        UUID.randomUUID(),
        "email@email.com",
        "닉네임",
        LocalDateTime.now()
    );
    when(userService.signUp(any(UserCreateCommand.class)))
        .thenReturn(result);

    // when & then
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").exists())
        .andExpect(jsonPath("$.email").value(request.email()))
        .andExpect(jsonPath("$.nickname").value(request.nickname()))
        .andExpect(jsonPath("$.createdAt").exists());

    // Command 변환값 검증 추가
    ArgumentCaptor<UserCreateCommand> commandCaptor = ArgumentCaptor.forClass(UserCreateCommand.class);
    verify(userService).signUp(commandCaptor.capture());
    UserCreateCommand capturedCommand = commandCaptor.getValue();
    assertThat(capturedCommand.email()).isEqualTo(request.email());
    assertThat(capturedCommand.nickname()).isEqualTo(request.nickname());
    assertThat(capturedCommand.password()).isEqualTo(request.password());
  }

  @ParameterizedTest
  @DisplayName("필드 형식이 유효하지 않으면 400으로 응답한다")
  @CsvSource({
      "'',             닉네임, PassWord123!",
      "email,          닉네임, PassWord123!",
      "email@email.com, '',   PassWord123!",
      "email@email.com, 'a',   PassWord123!",
      "email@email.com, 닉네임, ''",
      "email@email.com, 닉네임, 'PassWord123'",
      "email@email.com, 닉네임, 'PassWord123456789012!'",
      "email@email.com, 닉네임, 'Pw1!'"
  })
  void 형식_검증_실패시_400을_반환한다(String email, String nickname, String password) throws Exception{
    // given
    UserCreateRequest request = new UserCreateRequest(email, nickname, password);

    // when & then
    mockMvc.perform(post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").exists());


  }


  @Test
  @DisplayName("이메일이 중복되면 409로 응답한다.")
  void 이메일_중복시_409를_반환한다() throws Exception {
    // given
    UserCreateRequest request = new UserCreateRequest(
        "duplicate@email.com",
        "닉네임",
        "PassWord123!"
    );
    when(userService.signUp(any(UserCreateCommand.class)))
        .thenThrow(new DuplicateEmailException(request.email()));


    // when & then
    mockMvc.perform(post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").exists());


  }

}
