package com.codeit.sb13.monew.global.dto;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public record ApiErrorResponse(
        int status,
        String exceptionType,
        String code,
        String message,
        Map<String, Object> details,
        Instant timestamp
) {
    public static ApiErrorResponse of(MonewException e) {
        ApiErrorCode apiErrorCode = e.getApiErrorCode();
        return of(
                apiErrorCode.getStatus().value(),
                e.getClass().getSimpleName(),
                apiErrorCode.getCode(),
                apiErrorCode.getMessage(),
                e.getDetails()
        );
    }

    public static ApiErrorResponse of(
            int status,
            String exceptionType,
            String code,
            String message,
            Map<String, Object> details
    ) {
        return new ApiErrorResponse(
                status,
                exceptionType,
                code,
                message,
                copyDetails(details),
                Instant.now()
        );
    }

    private static Map<String, Object> copyDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }

        return details.entrySet()
                .stream()
                .filter(es -> hasText(es.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, ApiErrorResponse::getValueOrDefault));

    }

    private static boolean hasText(String key) {
        return StringUtils.hasText(key);
    }

    private static Object getValueOrDefault(Map.Entry<String, Object> es) {
        return es.getValue() == null ? "상세 정보가 없습니다." : es.getValue();
    }
}
