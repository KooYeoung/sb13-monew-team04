package com.codeit.sb13.monew.activity.service.impl;

import com.codeit.sb13.monew.activity.service.UserActivityService;
import com.codeit.sb13.monew.activity.service.UserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.activity.service.dto.UserActivityDto;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private final UserService userService;
    private final UserActivityReadSource activityReadSource;

    @Override
    public UserActivityDto userActivity(UUID userId) {
        User currentUser = findUserOrThrow(userId);
        UserActivitySections sections = activityReadSource.read(userId);

        return UserActivityDto.of(
                currentUser.getId(),
                currentUser.getEmail(),
                currentUser.getNickname(),
                currentUser.getCreatedAt(),
                sections.subscriptions(),
                sections.comments(),
                sections.commentLikes(),
                sections.articleViews()
        );
    }

    private User findUserOrThrow(UUID userId) {
        User currentUser = userService.findById(userId);
        if (currentUser.getDeletedAt() != null) {
            throw new UserNotFoundException(userId);
        }
        return currentUser;
    }

}
