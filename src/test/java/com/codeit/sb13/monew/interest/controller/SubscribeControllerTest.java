package com.codeit.sb13.monew.interest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.interest.controller.dto.SubscribeResponse;
import com.codeit.sb13.monew.interest.service.SubscribeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubscribeController.class)
class SubscribeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SubscribeService subscribeService;

    @Test
    @DisplayName("정상 요청 시 200과 구독 정보를 반환한다")
    void subscribe_validRequest_returns200() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        SubscribeResponse response = new SubscribeResponse(
                UUID.randomUUID(), interestId, "스포츠", List.of("축구"), 1L, LocalDateTime.now()
        );
        when(subscribeService.subscribe(interestId, requestUserId)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId)
                        .header("Monew-Request-User-ID", requestUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestId").value(interestId.toString()))
                .andExpect(jsonPath("$.interestName").value("스포츠"))
                .andExpect(jsonPath("$.interestSubscriberCount").value(1));

        verify(subscribeService).subscribe(interestId, requestUserId);
    }

    @Test
    @DisplayName("존재하지 않는 관심사를 구독하려 하면 404(INT_001)로 응답한다")
    void subscribe_nonExistingInterest_returns404() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        when(subscribeService.subscribe(any(), any()))
                .thenThrow(new InterestNotFoundException(interestId));

        // when & then
        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId)
                        .header("Monew-Request-User-ID", requestUserId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INT_001"));
    }

    @Test
    @DisplayName("Monew-Request-User-ID 헤더가 없으면 400으로 응답한다")
    void subscribe_missingUserIdHeader_returns400() throws Exception {
        UUID interestId = UUID.randomUUID();

        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("interestId가 UUID 형식이 아니면 400으로 응답한다")
    void subscribe_invalidUuidFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", "not-a-uuid")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("Monew-Request-User-ID 헤더가 UUID 형식이 아니면 400으로 응답한다")
    void subscribe_invalidUserIdHeaderFormat_returns400() throws Exception {
        UUID interestId = UUID.randomUUID();

        mockMvc.perform(post("/api/interests/{interestId}/subscriptions", interestId)
                        .header("Monew-Request-User-ID", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("정상 요청 시 204로 응답하고 구독을 취소한다")
    void unsubscribe_validRequest_returns204() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        doNothing().when(subscribeService).unsubscribe(interestId, requestUserId);

        // when & then
        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", interestId)
                        .header("Monew-Request-User-ID", requestUserId.toString()))
                .andExpect(status().isNoContent());

        verify(subscribeService).unsubscribe(interestId, requestUserId);
    }

    @Test
    @DisplayName("존재하지 않는 관심사의 구독을 취소하려 하면 404(INT_001)로 응답한다")
    void unsubscribe_nonExistingInterest_returns404() throws Exception {
        // given
        UUID interestId = UUID.randomUUID();
        UUID requestUserId = UUID.randomUUID();
        doThrow(new InterestNotFoundException(interestId))
                .when(subscribeService).unsubscribe(any(), any());

        // when & then
        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", interestId)
                        .header("Monew-Request-User-ID", requestUserId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INT_001"));
    }

    @Test
    @DisplayName("Monew-Request-User-ID 헤더가 없으면 400으로 응답한다")
    void unsubscribe_missingUserIdHeader_returns400() throws Exception {
        UUID interestId = UUID.randomUUID();

        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", interestId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("interestId가 UUID 형식이 아니면 400으로 응답한다")
    void unsubscribe_invalidUuidFormat_returns400() throws Exception {
        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", "not-a-uuid")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("Monew-Request-User-ID 헤더가 UUID 형식이 아니면 400으로 응답한다")
    void unsubscribe_invalidUserIdHeaderFormat_returns400() throws Exception {
        UUID interestId = UUID.randomUUID();

        mockMvc.perform(delete("/api/interests/{interestId}/subscriptions", interestId)
                        .header("Monew-Request-User-ID", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }
}
