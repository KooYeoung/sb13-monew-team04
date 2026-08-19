package com.codeit.sb13.monew.user.controller.dto;

import java.util.UUID;

public record UserLoginResponse(
    UUID userId,
    String email,
    String nickname
) {

}
