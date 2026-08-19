package com.codeit.sb13.monew.comment.controller.dto;

import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommentRegisterRequest(
    @NotNull(message = "댓글이 달릴 기사 ID는 필수입니다.")
    UUID articleId,

    @NotNull(message = "댓글 작성자 ID는 필수입니다.")
    UUID userId,

    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = 500, message = "댓글 내용은 500자를 초과할 수 없습니다.")
    String content
) {
    public CommentRegisterCommand toCommand() {
        return new CommentRegisterCommand(articleId, userId, content);
    }
}
