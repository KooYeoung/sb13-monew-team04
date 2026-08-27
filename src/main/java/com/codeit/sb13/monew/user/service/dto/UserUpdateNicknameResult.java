package com.codeit.sb13.monew.user.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserUpdateNicknameResult(
    UUID id,
    String nickname,
    LocalDateTime updatedAt,
    LocalDateTime createdAt,
    String email
) {

}
