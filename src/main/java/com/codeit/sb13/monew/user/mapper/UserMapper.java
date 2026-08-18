package com.codeit.sb13.monew.user.mapper;

import com.codeit.sb13.monew.user.controller.dto.UserCreateResponse;
import com.codeit.sb13.monew.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper (componentModel = "spring")
public interface UserMapper {

  @Mapping(source = "id", target = "userId")
  UserCreateResponse toResponse(User user);

}
