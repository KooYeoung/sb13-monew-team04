package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 구독 정보를 응답할 때 사용하는 DTO.
 *
 * <p>{@code interest*} 접두사가 붙은 필드들은 구독 시점의 값이 아니라, 응답 시점에
 * 조회한 관심사의 현재 상태(이름, 키워드, 구독자 수)를 담는다. 관심사 이름이나
 * 키워드가 바뀌어도 과거 구독 응답을 그대로 보관해두는 것이 아니라, 매 응답마다
 * 최신 상태를 다시 계산해 내려준다.</p>
 *
 * @param id 구독 id
 * @param interestId 구독한 관심사 id
 * @param interestName 관심사 이름
 * @param interestKeywords 관심사에 등록된 키워드 목록
 * @param interestSubscriberCount 관심사의 현재 구독자 수
 * @param createdAt 구독한 시각
 */
public record SubscribeResponse(
        UUID id,
        UUID interestId,
        String interestName,
        List<String> interestKeywords,
        long interestSubscriberCount,
        LocalDateTime createdAt
) {

    public static SubscribeResponse of(Subscribe subscribe, long interestSubscriberCount) {
        Interest interest = subscribe.getInterest();
        List<String> interestKeywords = interest.getKeywords().stream()
                .map(Keyword::getKeyword)
                .toList();

        return new SubscribeResponse(
                subscribe.getId(),
                interest.getId(),
                interest.getName(),
                interestKeywords,
                interestSubscriberCount,
                subscribe.getCreatedAt()
        );
    }
}
