package com.codeit.sb13.monew.user.service.dto;

import java.util.UUID;

public record UserUpdateNicknameCommand(
    UUID userId,
    String nickname
) {

}
