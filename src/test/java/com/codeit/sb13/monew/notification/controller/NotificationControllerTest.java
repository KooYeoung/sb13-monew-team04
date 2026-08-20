package com.codeit.sb13.monew.notification.controller;

import com.codeit.sb13.monew.global.exception.notification.NotificationNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.notification.controller.dto.NotificationResponse;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.mapper.NotificationMapper;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final String USER_ID_HEADER = "Monew-Request-User-ID";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    NotificationMapper mapper;

    @Nested
    @DisplayName("PATCH /api/notifications/{notificationId}")
    class ConfirmNotification {

        @Test
        @DisplayName("정상 요청이면 200과 확인된 알림을 반환한다.")
        void 정상_요청시_200_반환() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            NotificationResult result = new NotificationResult(
                    notificationId, userId, "내용", UUID.randomUUID(),
                    ResourceType.COMMENT, true, LocalDateTime.now(), LocalDateTime.now()
            );

            NotificationResponse response = new NotificationResponse(
                    result.id(), result.userId(), result.content(), result.resourceId(),
                    result.resourceType(), result.confirmed(), result.createdAt(), result.updatedAt()
            );

            when(notificationService.confirmNotification(notificationId, userId)).thenReturn(result);
            when(mapper.toResponse(result)).thenReturn(response);

            // when & then
            mockMvc.perform(patch("/api/notifications/{notificationId}", notificationId)
                            .header(USER_ID_HEADER, userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notificationId.toString()))
                    .andExpect(jsonPath("$.confirmed").value(true));
        }

        @Test
        @DisplayName("Monew-Request-User-ID 헤더가 없으면 400을 반환한다.")
        void 헤더_누락시_400_반환() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();

            // when & then
            mockMvc.perform(patch("/api/notifications/{notificationId}", notificationId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists());
        }

        @Test
        @DisplayName("존재하지 않는 notificationId면 404를 반환한다.")
        void 알림_없으면_404_반환() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(notificationService.confirmNotification(notificationId, userId))
                    .thenThrow(new NotificationNotFoundException(notificationId));

            // when & then
            mockMvc.perform(patch("/api/notifications/{notificationId}", notificationId)
                            .header(USER_ID_HEADER, userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.code").value("NTF_001"))
                    .andExpect(jsonPath("$.exceptionType").value("NotificationNotFoundException"));
        }

        @Test
        @DisplayName("본인 알림이 아니면 404를 반환한다.")
        void 본인_알림_아니면_404_반환() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(notificationService.confirmNotification(notificationId, userId))
                    .thenThrow(new NotificationNotFoundException(notificationId));

            // when & then
            mockMvc.perform(patch("/api/notifications/{notificationId}", notificationId)
                            .header(USER_ID_HEADER, userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.code").value("NTF_001"))
                    .andExpect(jsonPath("$.exceptionType").value("NotificationNotFoundException"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/notifications")
    class ConfirmAllNotifications {

        @Test
        @DisplayName("정상 요청이면 200과 확인된 알림 목록을 반환한다.")
        void 정상_요청시_200_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            NotificationResult result = new NotificationResult(
                    UUID.randomUUID(), userId, "내용", UUID.randomUUID(),
                    ResourceType.COMMENT, true, LocalDateTime.now(), LocalDateTime.now()
            );

            NotificationResponse response = new NotificationResponse(
                    result.id(), result.userId(), result.content(), result.resourceId(),
                    result.resourceType(), result.confirmed(), result.createdAt(), result.updatedAt()
            );

            when(notificationService.confirmAllNotifications(userId)).thenReturn(List.of(result));
            when(mapper.toResponse(result)).thenReturn(response);

            // when & then
            mockMvc.perform(patch("/api/notifications")
                            .header(USER_ID_HEADER, userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].confirmed").value(true));
        }

        @Test
        @DisplayName("Monew-Request-User-ID 헤더가 없으면 400을 반환한다.")
        void 헤더_누락시_400_반환() throws Exception {
            // when & then
            mockMvc.perform(patch("/api/notifications"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists());
        }

        @Test
        @DisplayName("요청자 정보가 없으면 404를 반환한다.")
        void 요청자_없으면_404를_반환한다() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            when(notificationService.confirmAllNotifications(userId))
                    .thenThrow(new UserNotFoundException(userId));

            // when & then
            mockMvc.perform(patch("/api/notifications")
                            .header(USER_ID_HEADER, userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").exists());
        }
    }
}