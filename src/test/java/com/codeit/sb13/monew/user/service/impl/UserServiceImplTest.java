package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
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

  @InjectMocks
  UserServiceImpl basicUserService;

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
    assertThatThrownBy(() -> basicUserService.signUp(request))
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

    // when
    basicUserService.signUp(request);

    // then
    verify(userRepository).save(any(User.class));


  }
  

}
