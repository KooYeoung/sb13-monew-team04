package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.dto.UserCreateCommand;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginCommand;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameCommand;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameResult;
import java.util.UUID;

public interface UserService {

   UserCreateResult signUp(UserCreateCommand command);

   UserLoginResult login(UserLoginCommand command);

   UserUpdateNicknameResult updateNickname(UserUpdateNicknameCommand command);

   User findById(UUID userId);

   User findActiveById(UUID userId); // 활성 사용자 조회

   void validateExists(UUID userId);

   void deleteUser(UUID userId);

   void hardDeleteUser(UUID userId);

   void autoDeleteExpiredUsers();


}
