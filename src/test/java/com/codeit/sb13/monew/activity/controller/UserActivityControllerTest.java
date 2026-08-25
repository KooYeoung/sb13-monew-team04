package com.codeit.sb13.monew.activity.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.monew.activity.service.UserActivityService;
import com.codeit.sb13.monew.activity.service.dto.RecentArticle;
import com.codeit.sb13.monew.activity.service.dto.RecentComment;
import com.codeit.sb13.monew.activity.service.dto.RecentCommentLike;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.codeit.sb13.monew.activity.service.dto.UserActivityDto;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserActivityController.class)
class UserActivityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserActivityService userActivityService;

    @Test
    @DisplayName("GET /api/user-activities/{userId} returns user activity")
    void getUserActivity_returnsUserActivity() throws Exception {
        UUID userId = UUID.randomUUID();
        UserActivityDto response = new UserActivityDto(
                userId,
                "user@example.com",
                "tester",
                LocalDateTime.of(2026, 8, 25, 13, 0),
                List.of(new RecentSubscribed(
                        UUID.randomUUID(),
                        LocalDateTime.of(2026, 8, 25, 13, 1),
                        UUID.randomUUID(),
                        "sports",
                        List.of("football", "baseball"),
                        12L
                )),
                List.of(new RecentComment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "comment article",
                        userId,
                        "tester",
                        "hello",
                        3L,
                        LocalDateTime.of(2026, 8, 25, 13, 2)
                )),
                List.of(new RecentCommentLike(
                        UUID.randomUUID(),
                        LocalDateTime.of(2026, 8, 25, 13, 3),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "liked article",
                        UUID.randomUUID(),
                        "commenter",
                        "liked comment",
                        5L,
                        LocalDateTime.of(2026, 8, 25, 13, 4)
                )),
                List.of(new RecentArticle(
                        UUID.randomUUID(),
                        userId,
                        LocalDateTime.of(2026, 8, 25, 13, 5),
                        UUID.randomUUID(),
                        ArticleSource.NAVER,
                        "https://example.com/article",
                        "viewed article",
                        LocalDateTime.of(2026, 8, 25, 12, 0),
                        "summary",
                        7L,
                        20L
                ))
        );
        when(userActivityService.userActivity(userId)).thenReturn(response);

        mockMvc.perform(get("/api/user-activities/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.nickname").value("tester"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.subscriptions[0].interestName").value("sports"))
                .andExpect(jsonPath("$.subscriptions[0].interestKeywords[0]").value("football"))
                .andExpect(jsonPath("$.comments[0].content").value("hello"))
                .andExpect(jsonPath("$.commentLikes[0].commentContent").value("liked comment"))
                .andExpect(jsonPath("$.articleViews[0].articleTitle").value("viewed article"));

        verify(userActivityService).userActivity(userId);
    }

    @Test
    @DisplayName("GET /api/user-activities/{userId} returns 404 when user is not found")
    void getUserActivity_userNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userActivityService.userActivity(userId)).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/user-activities/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());

        verify(userActivityService).userActivity(userId);
    }

    @Test
    @DisplayName("GET /api/user-activities/{userId} returns 400 when userId is invalid")
    void getUserActivity_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/user-activities/{userId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());

        verifyNoInteractions(userActivityService);
    }
}
