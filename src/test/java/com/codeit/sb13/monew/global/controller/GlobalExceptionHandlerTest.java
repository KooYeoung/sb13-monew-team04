package com.codeit.sb13.monew.global.controller;

import com.codeit.sb13.monew.global.dto.ApiErrorResponse;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("원인 예외가 있는 4xx MonewException은 응답 포맷을 유지하고 throwable을 warn 로그에 포함한다")
    void handlesClientErrorWithCause() {
        // given
        Throwable cause = new IllegalStateException("client root cause");
        MonewException exception = new TestMonewException(
                ApiErrorCode.ARTICLE_VIEW_CONFLICT,
                Map.of("articleId", "article-1"),
                cause
        );

        // when
        ResponseEntity<ApiErrorResponse> response = handler.handleMonewException(exception);

        // then
        assertErrorResponse(response, exception);
    }

    @Test
    @DisplayName("원인 예외가 없는 4xx MonewException은 응답 포맷을 유지하고 stack trace를 남기지 않는다")
    void handlesClientErrorWithoutCause(CapturedOutput output) {
        // given
        MonewException exception = new TestMonewException(
                ApiErrorCode.ARTICLE_VIEW_CONFLICT,
                Map.of("articleId", "article-1")
        );

        // when
        ResponseEntity<ApiErrorResponse> response = handler.handleMonewException(exception);

        // then
        assertErrorResponse(response, exception);
        assertThat(output)
                .contains("WARN")
                .doesNotContain("GlobalExceptionHandlerTest$TestMonewException");
    }

    @Test
    @DisplayName("원인 예외가 있는 4xx MonewException은 stack trace를 남긴다")
    void logsClientErrorCause(CapturedOutput output) {
        // given
        Throwable cause = new IllegalStateException("client root cause");
        MonewException exception = new TestMonewException(
                ApiErrorCode.ARTICLE_VIEW_CONFLICT,
                Map.of("articleId", "article-1"),
                cause
        );

        // when
        handler.handleMonewException(exception);

        // then
        assertThat(output)
                .contains("WARN")
                .contains("비즈니스 예외가 발생했습니다")
                .contains("java.lang.IllegalStateException: client root cause");
    }

    @Test
    @DisplayName("5xx MonewException은 기존처럼 throwable을 error 로그에 포함한다")
    void logsServerErrorWithThrowable(CapturedOutput output) {
        // given
        Throwable cause = new IllegalArgumentException("server root cause");
        MonewException exception = new TestMonewException(
                ApiErrorCode.INTERNAL_SERVER_ERROR,
                Map.of(),
                cause
        );

        // when
        ResponseEntity<ApiErrorResponse> response = handler.handleMonewException(exception);

        // then
        assertErrorResponse(response, exception);
        assertThat(output)
                .contains("ERROR")
                .contains("java.lang.IllegalArgumentException: server root cause");
    }

    private static void assertErrorResponse(
            ResponseEntity<ApiErrorResponse> response,
            MonewException exception
    ) {
        ApiErrorCode apiErrorCode = exception.getApiErrorCode();

        assertThat(response.getStatusCode().value()).isEqualTo(apiErrorCode.getStatus().value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(apiErrorCode.getStatus().value());
        assertThat(response.getBody().exceptionType()).isEqualTo(exception.getClass().getSimpleName());
        assertThat(response.getBody().code()).isEqualTo(apiErrorCode.getCode());
        assertThat(response.getBody().message()).isEqualTo(apiErrorCode.getMessage());
        assertThat(response.getBody().details()).isEqualTo(exception.getDetails());
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    private static class TestMonewException extends MonewException {

        private TestMonewException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
            super(apiErrorCode, details);
        }

        private TestMonewException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
            super(apiErrorCode, details, cause);
        }
    }
}
