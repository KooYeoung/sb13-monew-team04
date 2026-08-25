package com.codeit.sb13.monew.comment.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

// 댓글 목록 조회 쿼리 파라미터, 요청자 id는 헤더로 별도 수신
public record CommentSearchRequest(
    UUID articleId,
    String orderBy,
    Sort.Direction direction,
    String cursor,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
    UUID idAfter,
    int limit
) {
}
