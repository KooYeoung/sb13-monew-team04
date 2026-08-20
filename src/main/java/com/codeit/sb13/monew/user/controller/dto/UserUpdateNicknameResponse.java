package com.codeit.sb13.monew.user.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserUpdateNicknameResponse(
    UUID userId,
    String nickname,
    LocalDateTime updatedAt
) {

}
