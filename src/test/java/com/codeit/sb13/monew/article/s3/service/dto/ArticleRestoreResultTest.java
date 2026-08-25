package com.codeit.sb13.monew.article.s3.service.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArticleRestoreResult 단위 테스트")
class ArticleRestoreResultTest {

    private static final LocalDate RESTORE_DATE = LocalDate.of(2026, 8, 23);
    private static final UUID ARTICLE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    @DisplayName("복구 결과는 기사 ID 목록 크기로 복구 건수를 계산한다")
    void calculatesRestoredArticleCountFromRestoredArticleIds() {
        ArticleRestoreResult result = new ArticleRestoreResult(RESTORE_DATE, List.of(ARTICLE_ID), 999L);

        assertThat(result.restoreDate()).isEqualTo(RESTORE_DATE);
        assertThat(result.restoredArticleIds()).containsExactly(ARTICLE_ID);
        assertThat(result.restoredArticleCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("복구된 기사 ID 목록은 불변 복사본으로 보관한다")
    void keepsRestoredArticleIdsAsImmutableCopy() {
        List<UUID> restoredArticleIds = new ArrayList<>();
        restoredArticleIds.add(ARTICLE_ID);

        ArticleRestoreResult result = ArticleRestoreResult.of(RESTORE_DATE, restoredArticleIds);
        restoredArticleIds.clear();

        assertThat(result.restoredArticleIds()).containsExactly(ARTICLE_ID);
        assertThatThrownBy(() -> result.restoredArticleIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("복구 날짜가 없으면 예외가 발생한다")
    void throwsExceptionWhenRestoreDateIsNull() {
        assertThatThrownBy(() -> ArticleRestoreResult.of(null, List.of(ARTICLE_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("restoreDate must not be null");
    }

    @Test
    @DisplayName("복구된 기사 ID 목록이 없으면 예외가 발생한다")
    void throwsExceptionWhenRestoredArticleIdsIsNull() {
        assertThatThrownBy(() -> ArticleRestoreResult.of(RESTORE_DATE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("restoredArticleIds must not be null");
    }
}
