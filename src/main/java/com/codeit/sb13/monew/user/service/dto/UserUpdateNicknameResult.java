package com.codeit.sb13.monew.user.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserUpdateNicknameResult(
    UUID userId,
    String nickname,
    LocalDateTime updatedAt
) {

}
