package com.codeit.sb13.monew.comment.controller.dto;

import com.codeit.sb13.monew.comment.service.dto.CommentUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "수정할 댓글 정보")
public record CommentUpdateRequest(
    @Schema(description = "내용")
    @NotBlank(message = "댓글 내용은 필수입니다.") // 빈 문자열 또는 null 허용하지 않음
    @Size(min = 1, max = 500, message = "댓글 내용은 1자~500자까지만 입력 가능합니다.")
    String content
) {

  public CommentUpdateCommand toCommand(UUID commentId, UUID requestUserId) {
    return new CommentUpdateCommand(commentId, requestUserId, content);
  }
}
