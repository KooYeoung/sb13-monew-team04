package com.codeit.sb13.monew.notification.service.dto;

import com.codeit.sb13.monew.user.domain.User;

import java.util.List;
import java.util.UUID;

public record ArticlesForInterestDto(
        List<User> recipients,
        UUID resourceId,
        String interestName,
        int articleCount
) {
}
