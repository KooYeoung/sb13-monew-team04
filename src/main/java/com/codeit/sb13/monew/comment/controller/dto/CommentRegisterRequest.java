package com.codeit.sb13.monew.comment.controller.dto;

import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "댓글 정보")
public record CommentRegisterRequest(
    @Schema(description = "기사 ID")
    @NotNull(message = "댓글이 달릴 기사 ID는 필수입니다.")
    UUID articleId,

    @Schema(description = "요청 사용자 ID")
    @NotNull(message = "댓글 작성자 ID는 필수입니다.")
    UUID userId,

    @Schema(description = "내용")
    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(min = 1, max = 500, message = "댓글 내용은 1자~500자까지만 입력 가능합니다.")
    String content
) {
    public CommentRegisterCommand toCommand() {
        return new CommentRegisterCommand(articleId, userId, content);
    }
}
