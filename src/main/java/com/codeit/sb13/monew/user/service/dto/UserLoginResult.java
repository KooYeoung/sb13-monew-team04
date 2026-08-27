package com.codeit.sb13.monew.user.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserLoginResult(
    UUID id,
    String email,
    String nickname,
    LocalDateTime createdAt
) {

}
