package com.codeit.sb13.monew.user.service.dto;

import java.util.UUID;

public record UserLoginResult(
    UUID userId,
    String email,
    String nickname
) {

}
