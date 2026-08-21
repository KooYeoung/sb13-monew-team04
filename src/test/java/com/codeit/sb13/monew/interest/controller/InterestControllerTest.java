package com.codeit.sb13.monew.interest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.global.exception.interest.InterestSearchConditionInvalidException;
import com.codeit.sb13.monew.interest.controller.dto.InterestCreateRequest;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.InterestService;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;
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

    @Test
    @DisplayName("정상 요청이면 200과 관심사 목록을 반환한다")
    void search_validRequest_returns200() throws Exception {
        InterestResponse item = new InterestResponse(
                UUID.randomUUID(), "스포츠", List.of("축구"), 3L, true, LocalDateTime.now()
        );
        CursorPageResponseDto<InterestResponse> response =
                new CursorPageResponseDto<>(List.of(item), "스포츠", LocalDateTime.now().toString(), null, 1, 1L, false);
        when(interestService.search(any(InterestSearchCommand.class))).thenReturn(response);

        mockMvc.perform(get("/api/interests")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString())
                        .param("orderBy", "name")
                        .param("direction", "ASC")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("스포츠"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("orderBy가 허용된 값이 아니면 400으로 응답한다")
    void search_invalidOrderBy_returns400() throws Exception {
        mockMvc.perform(get("/api/interests")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString())
                        .param("orderBy", "invalid")
                        .param("direction", "ASC")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("direction이 허용된 값이 아니면 400으로 응답한다")
    void search_invalidDirection_returns400() throws Exception {
        mockMvc.perform(get("/api/interests")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString())
                        .param("orderBy", "name")
                        .param("direction", "invalid")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("Monew-Request-User-ID 헤더가 없으면 400으로 응답한다")
    void search_missingUserIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/interests")
                        .param("orderBy", "name")
                        .param("direction", "ASC")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("limit이 없으면 400으로 응답한다")
    void search_missingLimit_returns400() throws Exception {
        mockMvc.perform(get("/api/interests")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString())
                        .param("orderBy", "name")
                        .param("direction", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("limit이 1보다 작으면 400(INT_006)으로 응답한다")
    void search_limitLessThanOne_returns400() throws Exception {
        mockMvc.perform(get("/api/interests")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString())
                        .param("orderBy", "name")
                        .param("direction", "ASC")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INT_006"));
    }

    @Test
    @DisplayName("서비스 계층에서 조회 조건 오류가 올라오면 400(INT_006)으로 응답한다")
    void search_searchConditionInvalidFromService_returns400() throws Exception {
        // InterestRepositoryCustomImpl에서 구독자 수 기준 커서가 숫자가 아닐 때처럼,
        // 서비스 계층에서 InterestSearchConditionInvalidException이 올라오는 경우를 가정하고
        // 컨트롤러까지 도달했을 때 GlobalExceptionHandler가 400(INT_006)으로 응답하는지 확인한다.
        when(interestService.search(any(InterestSearchCommand.class)))
                .thenThrow(new InterestSearchConditionInvalidException(
                        "구독자 수 기준 커서 값이 올바르지 않습니다: 숫자아님"));

        mockMvc.perform(get("/api/interests")
                        .header("Monew-Request-User-ID", UUID.randomUUID().toString())
                        .param("orderBy", "subscriberCount")
                        .param("direction", "DESC")
                        .param("cursor", "숫자아님")
                        .param("after", LocalDateTime.now().toString())
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INT_006"));
    }
}