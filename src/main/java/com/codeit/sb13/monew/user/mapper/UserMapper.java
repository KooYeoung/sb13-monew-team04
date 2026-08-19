package com.codeit.sb13.monew.user.mapper;

import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.dto.UserCreateResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(source = "id", target = "userId")
  UserCreateResult toResult(User user);
}
