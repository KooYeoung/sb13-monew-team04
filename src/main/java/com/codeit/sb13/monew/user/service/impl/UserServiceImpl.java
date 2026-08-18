package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.mapper.UserMapper;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.UserService;
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
  public UserCreateResponse signUp(UserCreateRequest request) {
    boolean emailExists = userRepository.existsByEmail(request.email());

    if (emailExists) {
      throw new DuplicateEmailException(request.email());
    }

    String encode = passwordEncoder.encode(request.password());

    User user = User.builder()
        .email(request.email())
        .nickname(request.nickname())
        .password(encode)
        .build();

    try {
      User saveUser = userRepository.save(user);
      UserCreateResponse response = userMapper.toResponse(saveUser);
      return response;
    } catch (DataIntegrityViolationException e) {
      if (isEmailUniqueViolation(e)) {
        throw new DuplicateEmailException(request.email());
      }
      throw e;
    }
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause().getMessage();
    return message != null && message.contains("uk_users_email");
  }
}
