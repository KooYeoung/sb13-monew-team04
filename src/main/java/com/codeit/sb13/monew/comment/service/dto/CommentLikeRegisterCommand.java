package com.codeit.sb13.monew.comment.service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CommentLikeRegisterCommand(
    @NotNull(message = "좋아요를 누른 댓글 ID는 필수입니다.")
    UUID commentId,

    @NotNull(message = "좋아요를 누른 요청자 ID는 필수입니다.")
    UUID requestUserId
) {

}
