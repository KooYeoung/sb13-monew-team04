package com.codeit.sb13.monew.user.service.dto;

public record UserLoginCommand(
    String email,
    String password
) {

}
