package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HankyungRssCategory 단위 테스트")
class HankyungRssCategoryTest {

    @Test
    @DisplayName("key로 카테고리를 조회한다")
    void findsCategoryByKey() {
        assertThat(HankyungRssCategory.fromKey("all-news")).isEqualTo(HankyungRssCategory.ALL_NEWS);
        assertThat(HankyungRssCategory.fromKey("economy")).isEqualTo(HankyungRssCategory.ECONOMY);
    }

    @Test
    @DisplayName("key 앞뒤 공백과 대소문자를 정규화한다")
    void normalizesKey() {
        assertThat(HankyungRssCategory.fromKey(" FINANCE ")).isEqualTo(HankyungRssCategory.FINANCE);
    }

    @Test
    @DisplayName("key, 한글명, 출처를 보존한다")
    void preservesKeyLabelAndSource() {
        assertThat(HankyungRssCategory.ALL_NEWS.key()).isEqualTo("all-news");
        assertThat(HankyungRssCategory.ALL_NEWS.label()).isEqualTo("전체뉴스");
        assertThat(HankyungRssCategory.ALL_NEWS.source()).isEqualTo(ArticleSource.HANKYUNG);
    }

    @Test
    @DisplayName("잘못된 key면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenKeyIsInvalid() {
        assertThatThrownBy(() -> HankyungRssCategory.fromKey(null))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        assertThatThrownBy(() -> HankyungRssCategory.fromKey(" "))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        assertThatThrownBy(() -> HankyungRssCategory.fromKey("unknown"))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }
}
