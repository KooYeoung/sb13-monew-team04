package com.codeit.sb13.monew.article.service.naver.dto;

import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NaverNewsSearchRequest 단위 테스트")
class NaverNewsSearchRequestTest {

    @Test
    @DisplayName("query만 있으면 기본 검색 조건을 사용한다")
    void usesDefaultSearchConditionWhenOnlyQueryExists() {
        // when
        NaverNewsSearchRequest request = new NaverNewsSearchRequest("경제");

        // then
        assertThat(request.query()).isEqualTo("경제");
        assertThat(request.display()).isEqualTo(10);
        assertThat(request.start()).isEqualTo(1);
        assertThat(request.sort()).isEqualTo(NaverNewsSort.SIM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("query가 비어 있으면 예외 발생")
    void throwsExceptionWhenQueryIsBlank(String query) {
        assertThatThrownBy(() -> new NaverNewsSearchRequest(query))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("display는 1부터 100까지 허용한다")
    void allowsDisplayBetweenOneAndOneHundred(int display) {
        // when
        NaverNewsSearchRequest request = new NaverNewsSearchRequest("경제", null, display, null);

        // then
        assertThat(request.display()).isEqualTo(display);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    @DisplayName("display가 허용 범위를 벗어나면 예외 발생")
    void throwsExceptionWhenDisplayIsOutOfRange(int display) {
        assertThatThrownBy(() -> new NaverNewsSearchRequest("경제", null, display, null))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    @DisplayName("start는 1부터 1000까지 허용한다")
    void allowsStartBetweenOneAndOneThousand(int start) {
        // when
        NaverNewsSearchRequest request = new NaverNewsSearchRequest("경제", start, null, null);

        // then
        assertThat(request.start()).isEqualTo(start);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1001})
    @DisplayName("start가 허용 범위를 벗어나면 예외 발생")
    void throwsExceptionWhenStartIsOutOfRange(int start) {
        assertThatThrownBy(() -> new NaverNewsSearchRequest("경제", start, null, null))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }

    @Test
    @DisplayName("sort가 null이면 SIM을 사용한다")
    void usesSimWhenSortIsNull() {
        // when
        NaverNewsSearchRequest request = new NaverNewsSearchRequest("경제", null, null, null);

        // then
        assertThat(request.sort()).isEqualTo(NaverNewsSort.SIM);
    }

    @Test
    @DisplayName("지정한 sort를 보존한다")
    void preservesGivenSort() {
        // when
        NaverNewsSearchRequest request = new NaverNewsSearchRequest("경제", NaverNewsSort.DATE);

        // then
        assertThat(request.sort()).isEqualTo(NaverNewsSort.DATE);
    }
}
