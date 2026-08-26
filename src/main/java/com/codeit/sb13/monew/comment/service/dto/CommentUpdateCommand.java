package com.codeit.sb13.monew.comment.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommentUpdateCommand(
    @NotNull(message = "댓글 ID는 필수입니다.")
    UUID commentId,

    @NotNull(message = "요청자 ID는 필수입니다.")
    UUID requestUserId,

    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = 500, message = "댓글 내용은 500자를 초과할 수 없습니다.")
    String content
) {

}
