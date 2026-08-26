package com.codeit.sb13.monew.user.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserCreateResponse(
    UUID id,
    String email,
    String nickname,
    LocalDateTime createdAt
) {

}
