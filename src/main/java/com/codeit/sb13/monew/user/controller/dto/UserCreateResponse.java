package com.codeit.sb13.monew.user.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserCreateResponse(
    UUID userId,
    String email,
    String nickname,
    LocalDateTime createdAt
) {

}
