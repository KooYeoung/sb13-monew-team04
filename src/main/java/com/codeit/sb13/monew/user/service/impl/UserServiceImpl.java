package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.AlreadyDeletedUserException;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.exception.InvalidPasswordException;
import com.codeit.sb13.monew.user.exception.LoginUserNotFoundException;
import com.codeit.sb13.monew.user.mapper.UserMapper;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.UserHardDeleteExecutor;
import com.codeit.sb13.monew.user.service.UserService;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginCommand;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameCommand;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final UserHardDeleteExecutor userHardDeleteExecutor;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;

  @Transactional
  public UserCreateResult signUp(UserCreateCommand command) {
    log.debug("회원가입 요청 - email: {}", command.email());

    boolean emailExists = userRepository.existsByEmail(command.email());

    if (emailExists) {
      throw new DuplicateEmailException(command.email());
    }

    String encode = passwordEncoder.encode(command.password());

    User user = User.builder()
        .email(command.email())
        .nickname(command.nickname())
        .password(encode)
        .build();

    try {
      User saveUser = userRepository.saveAndFlush(user);
      UserCreateResult result = userMapper.toResult(saveUser);
      log.info("회원가입 성공 - email: {}", command.email());
      return result;

    } catch (DataIntegrityViolationException e) {
      if (isEmailUniqueViolation(e)) {
        throw new DuplicateEmailException(command.email());
      }

      log.error("회원가입 실패 - 예상치 못한 무결성 위반 - email: {}", command.email(), e);
      throw e;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public UserLoginResult login(UserLoginCommand command) {
    log.debug("로그인 요청 - email: {}", command.email());

    User user = userRepository.findByEmail(command.email())
        .orElseThrow(() -> new LoginUserNotFoundException(command.email()));

    LocalDateTime deletedAt = user.getDeletedAt();

    if (deletedAt != null) {
      throw new LoginUserNotFoundException(command.email());
    }

    String password = user.getPassword();

    boolean matches = passwordEncoder.matches(command.password(), password);
    if (!matches) {
      throw new InvalidPasswordException(command.email());
    }

    log.info("로그인 성공 - email: {}", command.email());
    return userMapper.toLoginResult(user);
  }

  @Override
  @Transactional
  public UserUpdateNicknameResult updateNickname(UserUpdateNicknameCommand command) {
    log.debug("닉네임 변경 요청 - userId: {}, nickname: {}", command.userId(), command.nickname());

    User user = userRepository.findById(command.userId())
        .orElseThrow(() -> new UserNotFoundException(command.userId()));

    user.updateNickname(command.nickname());
    User saveUser = userRepository.saveAndFlush(user);

    log.info("닉네임 변경 성공 - userId: {}, nickname: {}", command.userId(), command.nickname());
    return userMapper.toUpdateNicknameResult(saveUser);
  }

  @Override
  @Transactional(readOnly = true)
  public User findById(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public void validateExists(UUID userId) {
    if (!userRepository.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
  }

  @Override
  @Transactional
  public void deleteUser(UUID userId) {
    log.debug("논리 삭제 요청 - userId: {}", userId);
    int updatedCount = userRepository.softDeleteIfNotDeleted(userId, LocalDateTime.now());

    if (updatedCount == 0) {
      validateExists(userId);
      throw new AlreadyDeletedUserException(userId);
    }
    log.info("논리 삭제 성공 - userId: {}", userId);
  }

  @Override
  @Transactional
  public void hardDeleteUser(UUID userId) {
    userHardDeleteExecutor.hardDeleteUser(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public void autoDeleteExpiredUsers() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(1);
    List<User> expiredUsers = userRepository.findByDeletedAtBefore(threshold);
    log.info("사용자 자동 삭제 대상 조회 완료 - 대상 수: {}", expiredUsers.size());

    for (User user : expiredUsers) {
      try {
        userHardDeleteExecutor.hardDeleteExpiredUser(user.getId(), threshold);
      } catch (Exception e) {
        // 예외를 밖으로 던지지 않고 스케줄러 루프를 계속 진행하므로 이 error 로그는 필수 유지
        log.error("사용자 자동 물리 삭제 실패 - userId: {}, 사유: {}", user.getId(), e.getMessage());
      }
    }
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause().getMessage();
    return message != null && message.contains("uk_users_email");
  }
}