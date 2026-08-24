package com.codeit.sb13.monew.interest.repository.dto;

import com.codeit.sb13.monew.interest.domain.Interest;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code SubscribeRepository#findSubscribedInterestActivities}의 JPQL constructor projection 결과.
 *
 * <p>{@link com.codeit.sb13.monew.interest.domain.Subscribe} 엔티티만으로는 관심사의 현재
 * 구독자 수를 알 수 없으므로, 구독 행과 관심사 엔티티, 상관 서브쿼리로 계산한 활성
 * 구독자 수를 한 행으로 묶어 담는다.</p>
 *
 * @param id 구독 id
 * @param createdAt 구독 생성 시각. 관심사 생성 시각이 아니라 {@code subscriptions.created_at} 값이다.
 * @param interest 조회 시점의 관심사 엔티티
 * @param interestSubscriberCount 이 관심사를 구독 중인 논리삭제되지 않은 사용자 수
 */
public record SubscribedInterestActivityProjection(
        UUID id,
        LocalDateTime createdAt,
        Interest interest,
        Long interestSubscriberCount
) {

}
