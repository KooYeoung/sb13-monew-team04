package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChosunRssCategory 단위 테스트")
class ChosunRssCategoryTest {

    @Test
    @DisplayName("key로 카테고리를 조회한다")
    void findsCategoryByKey() {
        assertThat(ChosunRssCategory.fromKey("all")).isEqualTo(ChosunRssCategory.ALL);
        assertThat(ChosunRssCategory.fromKey("economy")).isEqualTo(ChosunRssCategory.ECONOMY);
    }

    @Test
    @DisplayName("key 앞뒤 공백과 대소문자를 정규화한다")
    void normalizesKey() {
        assertThat(ChosunRssCategory.fromKey(" CULTURE-LIFE ")).isEqualTo(ChosunRssCategory.CULTURE_LIFE);
    }

    @Test
    @DisplayName("key, 한글명, 출처를 보존한다")
    void preservesKeyLabelAndSource() {
        assertThat(ChosunRssCategory.ALL.key()).isEqualTo("all");
        assertThat(ChosunRssCategory.ALL.label()).isEqualTo("전체기사");
        assertThat(ChosunRssCategory.ALL.source()).isEqualTo(ArticleSource.CHOSUN);
    }

    @Test
    @DisplayName("잘못된 key면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenKeyIsInvalid() {
        assertThatThrownBy(() -> ChosunRssCategory.fromKey(null))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        assertThatThrownBy(() -> ChosunRssCategory.fromKey(" "))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        assertThatThrownBy(() -> ChosunRssCategory.fromKey("unknown"))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
    }
}
