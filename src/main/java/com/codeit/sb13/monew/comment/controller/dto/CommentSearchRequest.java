package com.codeit.sb13.monew.comment.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

// 댓글 목록 조회 쿼리 파라미터, 요청자 id는 헤더로 별도 수신
public record CommentSearchRequest(

    @Schema(description = "기사 ID", type = "string", format = "uuid")
    UUID articleId,

    @Schema(description = "정렬 속성 이름", type = "string", allowableValues = {"createdAt", "likeCount"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "정렬 기준은 필수입니다.")
    String orderBy,

    @Schema(description = "정렬 방향 (ASC, DESC)", type = "string", allowableValues = {"ASC", "DESC"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "정렬 방향은 필수입니다.")
    Sort.Direction direction,

    @Schema(description = "커서 값", type = "string")
    String cursor,

    @Schema(description = "보조 커서(createdAt) 값", type = "string", format = "date-time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,

    @Schema(description = "커서 페이지 크기", type = "integer", format = "int32", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    int limit
) {
}
