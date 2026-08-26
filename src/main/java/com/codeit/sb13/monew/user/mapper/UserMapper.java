package com.codeit.sb13.monew.user.mapper;

import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.controller.dto.UserLoginResponse;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import com.codeit.sb13.monew.user.service.dto.UserLoginResult;
import com.codeit.sb13.monew.user.service.dto.UserUpdateNicknameResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserCreateResult toResult(User user);

  UserLoginResult toLoginResult(User user);

  UserUpdateNicknameResult toUpdateNicknameResult(User user);

}
