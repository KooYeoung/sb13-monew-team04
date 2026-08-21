package com.codeit.sb13.monew.interest.service.dto;

/**
 * 관심사 목록 조회 시 정렬 기준.
 *
 * <p>API의 {@code orderBy} 파라미터는 {@code name}, {@code subscriberCount}처럼
 * camelCase 문자열로 들어오므로, {@link #from(String)}이 이 문자열을 받아
 * 해당하는 상수로 변환한다.</p>
 */
public enum InterestOrderBy {
    NAME,
    SUBSCRIBER_COUNT;

    /**
     * API에서 받은 camelCase 문자열을 {@link InterestOrderBy}로 변환한다.
     *
     * @param value {@code "name"} 또는 {@code "subscriberCount"}
     * @return 변환된 정렬 기준
     * @throws IllegalArgumentException 두 값 중 어느 것과도 일치하지 않는 경우
     */
    public static InterestOrderBy from(String value) {
        if ("name".equals(value)) {
            return NAME;
        }
        if ("subscriberCount".equals(value)) {
            return SUBSCRIBER_COUNT;
        }

        throw new IllegalArgumentException("정렬 기준이 올바르지 않습니다: " + value);
    }
}
