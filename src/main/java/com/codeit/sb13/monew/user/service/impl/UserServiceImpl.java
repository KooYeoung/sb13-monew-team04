package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.AlreadyDeletedUserException;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.exception.InvalidPasswordException;
import com.codeit.sb13.monew.user.exception.LoginUserNotFoundException;
import com.codeit.sb13.monew.user.mapper.UserMapper;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.UserService;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginCommand;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameCommand;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameResult;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;

  @Transactional
  public UserCreateResult signUp(UserCreateCommand command) {
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
      return result;
    } catch (DataIntegrityViolationException e) {
      if (isEmailUniqueViolation(e)) {
        throw new DuplicateEmailException(command.email());
      }
      throw e;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public UserLoginResult login(UserLoginCommand command) {
    User user = userRepository.findByEmail(command.email())
        .orElseThrow(() -> new LoginUserNotFoundException(command.email()));
    LocalDateTime deletedAt = user.getDeletedAt();
    if(deletedAt != null) {
      throw new LoginUserNotFoundException(command.email());
    }

    String password = user.getPassword();

    boolean matches = passwordEncoder.matches(command.password(), password);
    if (!matches) {
      throw new InvalidPasswordException(command.email());
    }
    return userMapper.toLoginResult(user);
  }

  @Override
  @Transactional
  public UserUpdateNicknameResult updateNickname(UserUpdateNicknameCommand command) {
    User user = userRepository.findById(command.userId())
        .orElseThrow(() -> new UserNotFoundException(command.userId()));
    user.updateNickname(command.nickname());
    User saveUser = userRepository.saveAndFlush(user);
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
    if(!userRepository.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }

  }

  @Override
  @Transactional
  public void deleteUser(UUID userId) {
    int updatedCount = userRepository.softDeleteIfNotDeleted(userId, LocalDateTime.now());

    if (updatedCount == 0) {
      validateExists(userId);
      throw new AlreadyDeletedUserException(userId);
    }
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause().getMessage();
    return message != null && message.contains("uk_users_email");
  }
}

