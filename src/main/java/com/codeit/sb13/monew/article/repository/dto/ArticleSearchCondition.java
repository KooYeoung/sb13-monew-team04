package com.codeit.sb13.monew.article.repository.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param keyword 검색어(제목 또는 요약에 포함). {@code null}/공백이면 전체 대상
 * @param sourceIn 출처 복수 선택. {@code null}이거나 비어 있으면 전체 출처
 * @param publishDateFrom 발행일 시작(포함). {@code null}이면 하한 없음
 * @param publishDateTo 발행일 종료(포함). {@code null}이면 상한 없음
 * @param requestUserId 요청자 id. 각 기사의 조회 여부를 계산하는 데 쓰임
 * @param orderBy 정렬 기준
 * @param direction 정렬 방향
 * @param cursor 이전 페이지 마지막 항목의 정렬 기준 값. 첫 페이지면 {@code null}
 * @param after 이전 페이지 마지막 항목의 생성 시각(보조 커서). 첫 페이지면 {@code null}
 * @param idAfter 이전 페이지 마지막 항목의 id(3차 커서). 첫 페이지면 {@code null}
 * @param limit 조회할 최대 개수
 */
public record ArticleSearchCondition(
        String keyword,
        List<ArticleSource> sourceIn,
        LocalDateTime publishDateFrom,
        LocalDateTime publishDateTo,
        ArticleOrderBy orderBy,
        Sort.Direction direction,
        String cursor,
        LocalDateTime after,
        UUID idAfter,
        int limit,
        UUID requestUserId
) {
}