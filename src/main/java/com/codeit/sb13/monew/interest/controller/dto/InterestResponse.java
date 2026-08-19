package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InterestResponse(
        UUID id,
        String name,
        List<String> keywords,
        long subscriberCount,
        boolean subscribedByMe,
        LocalDateTime createdAt
) {

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
