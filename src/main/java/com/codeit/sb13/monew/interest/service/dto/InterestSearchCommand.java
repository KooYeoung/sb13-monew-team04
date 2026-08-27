package com.codeit.sb13.monew.interest.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;

/**
 * 관심사 목록 조회 요청을 서비스 계층에 전달하기 위한 커맨드.
 *
 * @param keyword 검색어(관심사 이름 또는 키워드 텍스트에 포함). 없으면 전체 대상
 * @param orderBy 정렬 기준
 * @param direction 정렬 방향
 * @param cursor 이전 페이지 마지막 항목의 id. 첫 페이지 조회 시 {@code null}
 * @param after 이전 페이지 마지막 항목의 생성 시각(보조 커서). 첫 페이지 조회 시 {@code null}
 * @param limit 조회할 최대 개수
 * @param requestUserId 요청자 id. 각 관심사의 구독 여부를 계산하는 데 쓰인다
 */
public record InterestSearchCommand(
        String keyword,
        InterestOrderBy orderBy,
        Sort.Direction direction,
        UUID cursor,
        LocalDateTime after,
        int limit,
        UUID requestUserId
) {
}
