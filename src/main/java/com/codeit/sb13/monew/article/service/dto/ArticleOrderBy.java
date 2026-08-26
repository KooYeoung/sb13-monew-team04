package com.codeit.sb13.monew.article.service.dto;

public enum ArticleOrderBy {
    PUBLISH_DATE,
    COMMENT_COUNT,
    VIEW_COUNT;

    /**
     * API에서 받은 camelCase 문자열을 {@link ArticleOrderBy}로 변환한다.
     *
     * @param value {@code "publishDate"}, {@code "commentCount"}, {@code "viewCount"}
     * @return 변환된 정렬 기준
     * @throws IllegalArgumentException 세 값 중 어느 것과도 일치하지 않는 경우
     */
    public static ArticleOrderBy from(String value) {
        if ("publishDate".equals(value)) {
            return PUBLISH_DATE;
        }
        if ("commentCount".equals(value)) {
            return COMMENT_COUNT;
        }
        if ("viewCount".equals(value)) {
            return VIEW_COUNT;
        }

        throw new IllegalArgumentException("정렬 기준이 올바르지 않습니다: " + value);
    }
}