package com.codeit.sb13.monew.interest.repository.dto;

import java.util.List;

/**
 * {@code InterestRepositoryCustom#search}의 조회 결과를 담는 DTO.
 *
 * <p>{@link InterestSearchRow}가 관심사 엔티티와 구독자 수, 요청자의 구독 여부를
 * row 단위로 이미 함께 담고 있으므로, 페이지도 이를 별도 {@code Map}/{@code Set}으로
 * 다시 분해하지 않고 row 목록을 그대로 담는다. 서비스 계층은 각 row를 그대로
 * {@code InterestResponse}로 변환하면 된다.</p>
 *
 * @param rows 이번 페이지에 담긴 조회 결과 row 목록
 * @param hasNext 다음 페이지 존재 여부
 * @param totalElements 검색 조건을 만족하는 전체 관심사 수
 */
public record InterestSearchPage(
        List<InterestSearchRow> rows,
        boolean hasNext,
        long totalElements
) {
}
