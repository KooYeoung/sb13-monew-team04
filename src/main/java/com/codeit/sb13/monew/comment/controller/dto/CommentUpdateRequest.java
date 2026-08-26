package com.codeit.sb13.monew.comment.controller.dto;

import com.codeit.sb13.monew.comment.service.dto.CommentUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommentUpdateRequest(
    @NotBlank(message = "댓글 내용은 필수입니다.") // 빈 문자열 또는 null 허용하지 않음
    @Size(max = 500, message = "댓글 내용은 500자를 초과할 수 없습니다.")
    String content
) {

  public CommentUpdateCommand toCommand(UUID commentId, UUID requestUserId) {
    return new CommentUpdateCommand(commentId, requestUserId, content);
  }
}
