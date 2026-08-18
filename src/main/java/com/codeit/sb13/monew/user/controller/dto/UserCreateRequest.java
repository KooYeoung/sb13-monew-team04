package com.codeit.sb13.monew.user.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
    @NotBlank
    @Email
    String email,
    @NotBlank
    String nickname,
    @NotBlank
    String password
) {

}
