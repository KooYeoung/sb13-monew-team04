package com.codeit.sb13.monew.interest.repository.dto;

import com.codeit.sb13.monew.user.domain.User;

import java.util.UUID;

/**
 * {@code SubscribeRepository#findSubscriberUsersByInterestIds}의 JPQL constructor projection 결과.
 *
 * <p>여러 관심사의 구독자를 한 번의 쿼리로 조회하면, 결과 행 하나만으로는 그 사용자가
 * 어느 관심사를 구독해 나온 것인지 알 수 없다. 그래서 관심사 id와 사용자를 한 쌍으로
 * 묶어 담아, 호출부가 이 목록을 {@code interestId} 기준으로 그룹화해 관심사별 구독자
 * 목록을 복원할 수 있게 한다.</p>
 *
 * @param interestId 사용자가 구독 중인 관심사의 id
 * @param user       그 관심사를 활성 상태로 구독 중인 사용자
 */
public record InterestSubscriberRow(
        UUID interestId,
        User user
) {
}
