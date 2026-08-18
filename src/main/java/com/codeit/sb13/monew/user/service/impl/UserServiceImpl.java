package com.codeit.sb13.monew.user.service.impl;

import com.codeit.sb13.monew.user.controller.dto.UserCreateRequest;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.exception.DuplicateEmailException;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;


  public void signUp(UserCreateRequest request) {
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

    userRepository.save(user);
  }
}
