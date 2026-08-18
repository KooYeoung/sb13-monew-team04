package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.mapper.UserMapper;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {
  
  @Mock
  UserRepository userRepository;
  @Mock
  PasswordEncoder passwordEncoder;
  @Mock
  UserMapper userMapper;
  @Captor
  ArgumentCaptor<User> userCaptor;

  @InjectMocks
  UserServiceImpl userServiceImpl;

  @Test
  @DisplayName("이메일 중복된 경우, 회원가입 시 DuplicateEmailException을 던진다")
  void 회원가입_시_중복된_이메일이면_예외를_던진다() {
    // given
    UserCreateRequest request = new UserCreateRequest(
        "duplicate@example.com",
        "닉네임",
        "PassWord123!"
    );
    when(userRepository.existsByEmail(request.email()))
        .thenReturn(true);

    // when & then
    assertThatThrownBy(() -> userServiceImpl.signUp(request))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @DisplayName("정상적인 요철시에 사용자가 생성된다.")
  void 정상_값으로_요청_시_사용자가_생성된다() {
    // given
    UserCreateRequest request = new UserCreateRequest(
        "email@example.com",
        "닉네임",
        "PassWord123!"
    );
    when(userRepository.existsByEmail(request.email()))
        .thenReturn(false);
    when(passwordEncoder.encode(request.password()))
        .thenReturn("encodedPassword123");
    when(userMapper.toResponse(any(User.class)))
        .thenReturn(new UserCreateResponse(
            UUID.randomUUID(), "email@example.com",
            "닉네임", null));

    // when
    userServiceImpl.signUp(request);

    // then
    verify(userRepository).save(userCaptor.capture());
    User capturedUser = userCaptor.getValue();
    assertThat(capturedUser.getPassword()).isEqualTo("encodedPassword123");
    assertThat(capturedUser.getPassword()).isNotEqualTo(request.password());

  }

  @Test
  @DisplayName("이메일 중복 검사를 통과했지만 저장 시점에 DB 제약 위반이 발생하면 DuplicateEmailException을 던진다")
  void 저장_시점에_이메일_중복이_감지되면_예외를_던진다() {
    // given
    UserCreateRequest request = new UserCreateRequest(
        "email@email",
        "닉네임",
        "PassWord123!"
    );
    when(userRepository.existsByEmail(request.email()))
    .thenReturn(false);
    when(passwordEncoder.encode(request.password()))
        .thenReturn("encodedPassword123");
    when(userRepository.save(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

    // when & then
     assertThatThrownBy(() -> userServiceImpl.signUp(request))
         .isInstanceOf(DuplicateEmailException.class);




  }

}
