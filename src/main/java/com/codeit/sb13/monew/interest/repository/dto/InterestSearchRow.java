package com.codeit.sb13.monew.interest.repository.dto;

import com.codeit.sb13.monew.interest.domain.Interest;

/**
 * {@code InterestRepositoryCustomImpl#search}가 QueryDSL {@code Projections.constructor}로
 * 한 번에 조회하는 행(row) 하나를 담는 DTO.
 *
 * <p>관심사 엔티티와 함께, 엔티티 자체는 알 수 없는 구독자 수·요청자의 구독 여부를
 * 같은 쿼리 안에서 서브쿼리로 계산해 함께 담는다. 이렇게 하면 관심사 목록을 가져온 뒤
 * 구독 정보를 다시 조회해 id 기준으로 조립하는 별도 단계 없이, 조회 결과를 그대로
 * {@code InterestSearchPage}로 변환할 수 있다.</p>
 *
 * @param interest 관심사 엔티티
 * @param subscriberCount 이 관심사의 구독자 수
 * @param subscribedByMe 요청자가 이 관심사를 구독 중인지 여부. 요청자가 없으면(비로그인) 항상 {@code false}
 */
public record InterestSearchRow(
        Interest interest,
        Long subscriberCount,
        Boolean subscribedByMe
) {
}
