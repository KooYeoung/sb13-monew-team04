package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.mapper.UserMapper;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.UserService;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
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

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause().getMessage();
    return message != null && message.contains("uk_users_email");
  }
}
