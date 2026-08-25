package com.codeit.sb13.monew.interest.service.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import com.codeit.sb13.monew.interest.repository.dto.SubscribedInterestActivityProjection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 활동내역에서 구독 중인 관심사 하나를 표현하는 DTO.
 *
 * <p>{@code interest*} 필드는 구독 시점의 스냅샷이 아니라 조회 시점의 관심사 현재
 * 상태를 담는다. 관심사 이름이나 키워드가 수정되면 활동내역에서도 수정된 값을
 * 내려준다. 반면 {@code createdAt}은 관심사 생성 시각이 아니라 사용자가 해당
 * 관심사를 구독한 시각이다.</p>
 *
 * @param id                      구독 id
 * @param createdAt               구독 생성 시각
 * @param interestId              구독 중인 관심사 id
 * @param interestName            관심사 이름
 * @param interestKeywords        관심사에 등록된 키워드 목록
 * @param interestSubscriberCount 관심사의 현재 활성 구독자 수
 */
public record SubscribedInterestActivityDto(
        UUID id,
        LocalDateTime createdAt,
        UUID interestId,
        String interestName,
        List<String> interestKeywords,
        Long interestSubscriberCount
) {

    /**
     * 리포지토리 projection 한 행을 활동내역 응답 DTO로 변환한다.
     *
     * <p>키워드는 {@link Interest#getKeywords()}를 통해 조회한다. 관심사 목록을 여러 건
     * 한 번에 변환할 때는 {@code Interest.keywords}의 배치 로딩 설정에 따라 여러 관심사의
     * 키워드가 한 번의 지연 로딩 쿼리로 묶여 조회된다.</p>
     */
    public static SubscribedInterestActivityDto from(SubscribedInterestActivityProjection projection) {
        Interest interest = projection.interest();
        return new SubscribedInterestActivityDto(
                projection.id(),
                projection.createdAt(),
                interest.getId(),
                interest.getName(),
                getKeywords(interest),
                projection.interestSubscriberCount()
        );
    }

    private static List<String> getKeywords(Interest interest) {
        return interest.getKeywords()
                .stream()
                .map(Keyword::getKeyword)
                .toList();
    }
}
