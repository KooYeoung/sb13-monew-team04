package com.codeit.sb13.monew.interest.repository.dto;

import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;

/**
 * {@link com.codeit.sb13.monew.interest.repository.InterestRepositoryCustom#search}에
 * 전달하는 조회 조건을 담는 DTO.
 *
 * @param keyword 검색어(관심사 이름 또는 키워드 텍스트에 포함). {@code null}/공백이면 전체 대상
 * @param orderBy 정렬 기준
 * @param direction 정렬 방향
 * @param cursor 이전 페이지 마지막 항목의 id. 첫 페이지 조회 시 {@code null}
 * @param after 이전 페이지 마지막 항목의 생성 시각(보조 커서). 첫 페이지 조회 시 {@code null}
 * @param limit 조회할 최대 개수
 * @param requestUserId 요청자 id. {@code null}이면 구독 여부는 계산하지 않는다
 */
public record InterestSearchCondition(
        String keyword,
        InterestOrderBy orderBy,
        Sort.Direction direction,
        UUID cursor,
        LocalDateTime after,
        int limit,
        UUID requestUserId
) {
}
