package com.codeit.sb13.monew.user.service.dto;

public record UserCreateCommand(
    String email,
    String nickname,
    String password
) {

}
