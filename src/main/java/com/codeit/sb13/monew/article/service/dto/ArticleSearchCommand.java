package com.codeit.sb13.monew.article.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 기사 목록 조회 요청을 서비스 계층에 전달하기 위한 커맨드.
public record ArticleSearchCommand(
        String keyword,
        List<ArticleSource> sourceIn,
        LocalDateTime publishDateFrom,
        LocalDateTime publishDateTo,
        ArticleOrderBy orderBy,
        Sort.Direction direction,
        UUID cursor,
        LocalDateTime after,
        int limit,
        UUID requestUserId
) {
}