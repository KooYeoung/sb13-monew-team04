package com.codeit.sb13.monew.activity.service.dto;

import com.codeit.sb13.monew.interest.service.dto.SubscribedInterestActivityDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RecentSubscribed(
        UUID id,
        LocalDateTime createdAt,
        UUID interestId,
        String interestName,
        List<String> interestKeywords,
        Long interestSubscriberCount
) {

    public static RecentSubscribed from(SubscribedInterestActivityDto recent) {

        return new RecentSubscribed(
                recent.id(),
                recent.createdAt(),
                recent.interestId(),
                recent.interestName(),
                getKeywords(recent.interestKeywords()),
                recent.interestSubscriberCount()
        );
    }

    private static List<String> getKeywords(List<String> interestKeywords) {
        return interestKeywords.stream().toList();
    }
}
