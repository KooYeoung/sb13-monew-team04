package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("YonhapRssCategory 단위 테스트")
class YonhapRssCategoryTest {

    @Test
    @DisplayName("key로 카테고리를 조회한다")
    void findsCategoryByKey() {
        assertThat(YonhapRssCategory.fromKey("latest")).isEqualTo(YonhapRssCategory.LATEST);
        assertThat(YonhapRssCategory.fromKey("economy")).isEqualTo(YonhapRssCategory.ECONOMY);
    }

    @Test
    @DisplayName("key 앞뒤 공백과 대소문자를 정규화한다")
    void normalizesKey() {
        assertThat(YonhapRssCategory.fromKey(" POLITICS ")).isEqualTo(YonhapRssCategory.POLITICS);
    }

    @Test
    @DisplayName("key, 한글명, 출처를 보존한다")
    void preservesKeyLabelAndSource() {
        assertThat(YonhapRssCategory.LATEST.key()).isEqualTo("latest");
        assertThat(YonhapRssCategory.LATEST.label()).isEqualTo("최신");
        assertThat(YonhapRssCategory.LATEST.source()).isEqualTo(ArticleSource.YEONHAP);
    }

    @Test
    @DisplayName("잘못된 key면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenKeyIsInvalid() {
        assertThatThrownBy(() -> YonhapRssCategory.fromKey(null))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        assertThatThrownBy(() -> YonhapRssCategory.fromKey(" "))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        assertThatThrownBy(() -> YonhapRssCategory.fromKey("unknown"))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }
}
