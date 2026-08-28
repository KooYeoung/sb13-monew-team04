package com.codeit.sb13.monew.article.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArticleViewTest {

    @Test
    @DisplayName("create()는 조회 기록을 ACTIVE 노출 상태로 생성한다")
    void create() {
        // given
        Article article = Article.create(
                "기사 제목",
                "기사 요약",
                "https://test.com/article",
                LocalDateTime.now(),
                ArticleSource.NAVER
        );
        User user = User.builder()
                .email("test@test.com")
                .nickname("테스트 사용자")
                .password("Abcd!")
                .build();
        LocalDateTime viewedAt = LocalDateTime.of(2026, 8, 28, 16, 40);

        // when
        ArticleView articleView = ArticleView.create(article, user, viewedAt);

        // then
        assertThat(articleView.getArticle()).isEqualTo(article);
        assertThat(articleView.getUser()).isEqualTo(user);
        assertThat(articleView.getViewedAt()).isEqualTo(viewedAt);
        assertThat(articleView.getVisibilityStatus()).isEqualTo(ActivityVisibilityStatus.ACTIVE);
    }
}
