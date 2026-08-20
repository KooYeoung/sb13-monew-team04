package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 관심사 정보를 클라이언트에 응답하기 위한 DTO.
 *
 * @param id 관심사 id
 * @param name 관심사 이름
 * @param keywords 관심사에 속한 키워드 텍스트 목록
 * @param subscriberCount 이 관심사를 구독 중인 사용자 수
 * @param subscribedByMe 이 응답을 요청한 사용자가 구독 중인지 여부
 * @param createdAt 관심사 생성 시각
 */
public record InterestResponse(
        UUID id,
        String name,
        List<String> keywords,
        long subscriberCount,
        boolean subscribedByMe,
        LocalDateTime createdAt
) {

    /**
     * 관심사 엔티티와 구독 관련 정보를 조합해 응답 DTO를 생성한다.
     *
     * <p>{@code subscriberCount}, {@code subscribedByMe}는 {@link Interest} 자체가
     * 알 수 없는 값(구독 테이블을 조회해야 알 수 있는 값)이라 호출하는 쪽에서
     * 계산해 넘겨줘야 한다.</p>
     *
     * @param interest 응답으로 변환할 관심사 엔티티
     * @param subscriberCount 이 관심사를 구독 중인 사용자 수
     * @param subscribedByMe 요청한 사용자의 구독 여부
     * @return 변환된 응답 DTO
     */
    public static InterestResponse of(Interest interest, long subscriberCount, boolean subscribedByMe) {
        List<String> keywords = interest.getKeywords().stream()
                .map(Keyword::getKeyword)
                .toList();

        return new InterestResponse(
                interest.getId(),
                interest.getName(),
                keywords,
                subscriberCount,
                subscribedByMe,
                interest.getCreatedAt()
        );
    }
}
