package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.exception.InvalidPasswordException;
import com.codeit.sb13.monew.user.exception.LoginUserNotFoundException;
import com.codeit.sb13.monew.user.mapper.UserMapper;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginCommand;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import java.util.Optional;
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
import static org.mockito.Mockito.never;
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
    UserCreateCommand command = new UserCreateCommand(
        "duplicate@example.com",
        "닉네임",
        "PassWord123!"
    );
    when(userRepository.existsByEmail(command.email()))
        .thenReturn(true);

    // when & then
    assertThatThrownBy(() -> userServiceImpl.signUp(command))
        .isInstanceOf(DuplicateEmailException.class);

    verify(passwordEncoder, never()).encode(any());
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("정상적인 요청 시에 사용자가 생성된다.")
  void 정상_값으로_요청_시_사용자가_생성된다() {
    // given
    UserCreateCommand command = new UserCreateCommand(
        "email@example.com",
        "닉네임",
        "PassWord123!"
    );
    UserCreateResult expectedResponse = new UserCreateResult(
        UUID.randomUUID(), "email@example.com",
        "닉네임", null);

    when(userRepository.existsByEmail(command.email()))
        .thenReturn(false);
    when(passwordEncoder.encode(command.password()))
        .thenReturn("encodedPassword123");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResult(any(User.class)))
        .thenReturn(expectedResponse);

    // when
    UserCreateResult actualResult = userServiceImpl.signUp(command);

    // then
    verify(userRepository).saveAndFlush(userCaptor.capture());
    User capturedUser = userCaptor.getValue();
    assertThat(capturedUser.getPassword()).isEqualTo("encodedPassword123");
    assertThat(capturedUser.getPassword()).isNotEqualTo(command.password());
    assertThat(actualResult).isEqualTo(expectedResponse);
  }

  @Test
  @DisplayName("이메일 중복 검사를 통과했지만 저장 시점에 DB 제약 위반이 발생하면 DuplicateEmailException을 던진다")
  void 저장_시점에_이메일_중복이_감지되면_예외를_던진다() {
    // given
    UserCreateCommand command = new UserCreateCommand(
        "email@email",
        "닉네임",
        "PassWord123!"
    );
    when(userRepository.existsByEmail(command.email()))
        .thenReturn(false);
    when(passwordEncoder.encode(command.password()))
        .thenReturn("encodedPassword123");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_users_email\""));

    // when & then
    assertThatThrownBy(() -> userServiceImpl.signUp(command))
        .isInstanceOf(DuplicateEmailException.class);
    verify(userRepository).existsByEmail(command.email());
  }

  @Test
  @DisplayName("이메일과 무관한 DB 제약 위반이면 원래 예외를 그대로 던진다")
  void 이메일과_무관한_제약_위반이면_원래_예외를_던진다() {
    // given
    UserCreateCommand command = new UserCreateCommand(
        "email@example.com",
        "닉네임",
        "PassWord123!"
    );
    when(userRepository.existsByEmail(command.email()))
        .thenReturn(false);
    when(passwordEncoder.encode(command.password()))
        .thenReturn("encodedPassword123");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException(
            "null value in column \"nickname\" violates not-null constraint"));

    // when & then
    assertThatThrownBy(() -> userServiceImpl.signUp(command))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인하면 LoginUserNotFoundException을 던진다.")
  void 존재하지_않는_이메일로_로그인시_예외를_던진다() {
    // given
    UserLoginCommand command = new UserLoginCommand(
        "email@email.com",
        "PassWord123!"
    );
    when(userRepository.findByEmail(command.email()))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userServiceImpl.login(command))
        .isInstanceOf(LoginUserNotFoundException.class);
    verify(passwordEncoder, never()).matches(any(), any());
    verify(userMapper, never()).toLoginResult(any());
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 InvalidPasswordException을 던진다.")
  void 비밀번호가_일치하지_않으면_예외를_던진다() {
    // given
    UserLoginCommand command = new UserLoginCommand(
        "email@email.com",
        "PassWord123!"
    );
    User user = User.builder()
        .email("email@email.com")
        .nickname("닉네임")
        .password("encodedPassword123")
        .build();
    when(userRepository.findByEmail(command.email()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(command.password(), user.getPassword()))
        .thenReturn(false);

    // when & then
    assertThatThrownBy(() -> userServiceImpl.login(command))
        .isInstanceOf(InvalidPasswordException.class);
    verify(userMapper, never()).toLoginResult(any());
  }

  @Test
  @DisplayName("이메일/비밀번호가 올바르면 로그인에 성공한다.")
  void 올바른_이메일과_비밀번호라면_로그인_성공() {
    // given
    UserLoginCommand command = new UserLoginCommand(
        "email@email.com",
        "PassWord123!"
    );
    User user = User.builder()
        .email("email@email.com")
        .nickname("닉네임")
        .password("encodedPassword123")
        .build();
    UserLoginResult expectedResult = new UserLoginResult(
        UUID.randomUUID(), "email@email.com", "닉네임"
    );
    when(userMapper.toLoginResult(user))
        .thenReturn(expectedResult);
    when(userRepository.findByEmail(command.email()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(command.password(), user.getPassword()))
        .thenReturn(true);

    // when
    UserLoginResult actualResult = userServiceImpl.login(command);

    // then
    assertThat(actualResult).isEqualTo(expectedResult);
  }

}
