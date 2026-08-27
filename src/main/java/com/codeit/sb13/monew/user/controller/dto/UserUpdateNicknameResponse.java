package com.codeit.sb13.monew.user.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserUpdateNicknameResponse(
    UUID id,
    String nickname,
    LocalDateTime updatedAt,
    LocalDateTime createdAt,
    String email
) {

}
