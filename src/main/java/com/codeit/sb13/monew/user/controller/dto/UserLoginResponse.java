package com.codeit.sb13.monew.user.controller.dto;

import java.util.UUID;

public record UserLoginResponse(
    UUID id,
    String email,
    String nickname
) {

}
