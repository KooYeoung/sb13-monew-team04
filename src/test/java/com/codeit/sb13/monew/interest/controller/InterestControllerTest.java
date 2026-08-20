package com.codeit.sb13.monew.interest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.interest.controller.dto.InterestCreateRequest;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.InterestService;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(InterestController.class)
class InterestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    InterestService interestService;

    @Test
    @DisplayName("정상 요청 시 201과 관심사 정보를 반환한다")
    void register_validRequest_returns201() throws Exception {
        // given
        InterestCreateRequest request = new InterestCreateRequest("스포츠", List.of("축구", "야구"));
        InterestResponse response = new InterestResponse(
                UUID.randomUUID(), "스포츠", List.of("축구", "야구"), 0L, false, LocalDateTime.now()
        );
        when(interestService.create(any(InterestCreateCommand.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/interests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("스포츠"))
                .andExpect(jsonPath("$.keywords[0]").value("축구"))
                .andExpect(jsonPath("$.subscriberCount").value(0))
                .andExpect(jsonPath("$.subscribedByMe").value(false));

        ArgumentCaptor<InterestCreateCommand> commandCaptor = ArgumentCaptor.forClass(InterestCreateCommand.class);
        verify(interestService).create(commandCaptor.capture());
        InterestCreateCommand capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand.name()).isEqualTo(request.name());
        assertThat(capturedCommand.keywords()).isEqualTo(request.keywords());
    }

    @Test
    @DisplayName("이름이 공백이면 400으로 응답한다")
    void register_blankName_returns400() throws Exception {
        InterestCreateRequest request = new InterestCreateRequest("", List.of("축구"));

        mockMvc.perform(post("/api/interests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("이름이 50자를 넘으면 400으로 응답한다")
    void register_tooLongName_returns400() throws Exception {
        InterestCreateRequest request = new InterestCreateRequest("가".repeat(51), List.of("축구"));

        mockMvc.perform(post("/api/interests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("키워드가 비어 있으면 400으로 응답한다")
    void register_emptyKeywords_returns400() throws Exception {
        InterestCreateRequest request = new InterestCreateRequest("스포츠", List.of());

        mockMvc.perform(post("/api/interests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("이름이 중복되면 409로 응답한다")
    void register_duplicateName_returns409() throws Exception {
        InterestCreateRequest request = new InterestCreateRequest("스포츠", List.of("축구"));
        when(interestService.create(any(InterestCreateCommand.class)))
                .thenThrow(new InterestNameDuplicatedException(request.name()));

        mockMvc.perform(post("/api/interests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INT_005"));
    }
}