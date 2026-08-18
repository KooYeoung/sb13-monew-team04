package com.codeit.sb13.monew.global.controller;

import com.codeit.sb13.monew.global.dto.ApiErrorResponse;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MonewException.class)
    public ResponseEntity<ApiErrorResponse> handleMonewException(MonewException e) {

        HttpStatus status = e.getApiErrorCode().getStatus();

        if (status.is5xxServerError()) {
            log.error("서버 오류가 발생했습니다. errorCode={}, message={}", e.getApiErrorCode(), e.getMessage(), e);
        } else {
            log.warn("비즈니스 예외가 발생했습니다. errorCode={}, message={}", e.getApiErrorCode(), e.getMessage());
        }

        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(e));

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {

        Map<String, Object> details = getValidationDetails(e);

        log.warn("요청 데이터 검증에 실패했습니다. details={}", details);

        ApiErrorCode apiErrorCode = ApiErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(apiErrorCode.getStatus())
                .body(ApiErrorResponse.of(
                        apiErrorCode.getStatus().value(),
                        e.getClass().getSimpleName(),
                        apiErrorCode.getCode(),
                        apiErrorCode.getMessage(),
                        details
                ));

    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception e) {
        ApiErrorCode errorCode = ApiErrorCode.INVALID_REQUEST;

        log.warn("잘못된 요청 형식입니다. errorCode={}, exceptionType={}, message={}",
                errorCode,
                e.getClass().getSimpleName(),
                e.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(
                        errorCode.getStatus().value(),
                        e.getClass().getSimpleName(),
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        Map.of("reason", "요청 형식이 올바르지 않습니다.")
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 서버 오류가 발생했습니다.", e);
        ApiErrorCode apiErrorCode = ApiErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(apiErrorCode.getStatus())
                .body(ApiErrorResponse.of(
                        apiErrorCode.getStatus().value(),
                        "Exception",
                        apiErrorCode.getCode(),
                        apiErrorCode.getMessage(),
                        Map.of()
                ));
    }


    private Map<String, Object> getValidationDetails(MethodArgumentNotValidException e) {
        return Collections.unmodifiableMap(
                Stream.concat(
                                e.getBindingResult().getGlobalErrors().stream(),
                                e.getBindingResult().getFieldErrors().stream()
                        )
                        .collect(Collectors.groupingBy(
                                this::getValidationErrorKey,
                                Collectors.mapping(this::getValidationErrorDetail, Collectors.toList())
                        )));
    }

    private String getValidationErrorKey(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }

        return error.getObjectName();
    }

    private String getValidationErrorDetail(ObjectError error) {
        return error.getDefaultMessage() == null ? "상세 내용이 없습니다." : error.getDefaultMessage();
    }

}
