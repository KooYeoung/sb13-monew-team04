package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;

public record CommentSearchCommand (
    UUID articleId,
    CommentOrderBy orderBy,
    Sort.Direction direction,
    String cursor,
    LocalDateTime after,
    UUID idAfter,
    int limit,
    UUID requestUserId
){}
