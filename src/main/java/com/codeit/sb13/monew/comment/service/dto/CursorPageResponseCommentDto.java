package com.codeit.sb13.monew.comment.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "커서 기반 페이지 응답")
public record CursorPageResponseCommentDto(

    @Schema(description = "페이지 내용")
    List<CommentDto> content,

    @Schema(description = "다음 페이지 커서")
    String nextCursor,

    @Schema(description = "다음 보조 커서(마지막 요소의 생성 시간)", format = "date-time", example = "2025-04-06T15:04:00Z")
    String nextAfter,

    @Schema(description = "페이지 크기", example = "10")
    Integer size,

    @Schema(description = "총 요소 수", example = "100")
    Long totalElements,

    @Schema(description = "다음 페이지 여부", example = "true")
    Boolean hasNext
) {
}
