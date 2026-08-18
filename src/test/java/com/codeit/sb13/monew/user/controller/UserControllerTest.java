package com.codeit.sb13.monew.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.service.UserService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
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

    UserCreateResponse response = new UserCreateResponse(
        UUID.randomUUID(),
        "email@email.com",
        "닉네임",
        LocalDateTime.now()
    );
    when(userService.signUp(request))
        .thenReturn(response);

    // when & then
    mockMvc.perform(post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("이메일 검증 실패 시 400으로 응답한다.")
  void 이메일_검증_실패시_400을_반환한다() throws Exception{
    // given
    UserCreateRequest request = new UserCreateRequest(
        "email",
        "닉네임",
        "PassWord123!"
    );

    // when & then
    mockMvc.perform(post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());


  }


}
