package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;

public record CommentSearchCommand (
    UUID articleId,

    @NotNull CommentOrderBy orderBy,

    @NotNull Sort.Direction direction,

    String cursor,

    LocalDateTime after,

    @Min(1) int limit,

    @NotNull UUID requestUserId
){}
