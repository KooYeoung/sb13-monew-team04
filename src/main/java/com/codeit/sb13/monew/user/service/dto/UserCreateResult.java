package com.codeit.sb13.monew.user.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserCreateResult(
    UUID id,
    String email,
    String nickname,
    LocalDateTime createdAt
) {
}