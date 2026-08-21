package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;

/**
 * QueryDSL로 구현하는 관심사 목록 조회.
 *
 * <p>동적 검색어 필터, 정렬 기준 선택, 커서 기반 페이지네이션처럼
 * Spring Data JPA의 메서드 이름 규칙이나 {@code @Query}만으로는 표현하기
 * 어려운 조회라 커스텀 구현으로 분리했다.</p>
 */
public interface InterestRepositoryCustom {

    /**
     * 조건에 맞는 관심사 목록을 커서 기반으로 조회한다.
     *
     * @param condition 검색어, 정렬 기준/방향, 커서, 요청자 등 조회 조건
     * @return 조회 결과
     */
    InterestSearchPage search(InterestSearchCondition condition);
}
