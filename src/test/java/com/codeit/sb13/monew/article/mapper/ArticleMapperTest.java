package com.codeit.sb13.monew.article.mapper;

import com.codeit.sb13.monew.article.controller.dto.ArticleDto;
import com.codeit.sb13.monew.article.controller.dto.ArticleViewDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ArticleMapper 단위 테스트")
class ArticleMapperTest {

    private final ArticleMapper articleMapper = new ArticleMapperImpl();

    private UUID articleId;
    private UUID userId;
    private LocalDateTime publishDate;
    private Article article;
    private User user;

    @BeforeEach
    void setUp() {
        articleId = UUID.randomUUID();
        userId = UUID.randomUUID();
        publishDate = LocalDateTime.now().minusDays(1);

        article = Article.create(
                "Test Article",
                "Test Summary",
                "https://example.com/article",
                publishDate,
                ArticleSource.NAVER
        );
        ReflectionTestUtils.setField(article, "id", articleId);

        user = User.builder()
                .email("test@example.com")
                .nickname("tester")
                .password("encoded-password")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Test
    @DisplayName("Article을 ArticleDto로 변환한다")
    void testToDto() {
        // when
        ArticleDto result = articleMapper.toDto(article, true, 3, 7);

        // then
        assertThat(result.id()).isEqualTo(articleId);
        assertThat(result.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(result.sourceUrl()).isEqualTo("https://example.com/article");
        assertThat(result.title()).isEqualTo("Test Article");
        assertThat(result.publishDate()).isEqualTo(publishDate);
        assertThat(result.summary()).isEqualTo("Test Summary");
        assertThat(result.commentCount()).isEqualTo(3);
        assertThat(result.viewCount()).isEqualTo(7);
        assertThat(result.viewedByMe()).isTrue();
    }

    @Test
    @DisplayName("viewedByMe는 전달받은 값을 그대로 반영한다")
    void testToDtoViewedByMeFalse() {
        assertThat(articleMapper.toDto(article, false, 0, 0).viewedByMe()).isFalse();
    }

    @Test
    @DisplayName("ArticleView를 ArticleViewDto로 변환한다")
    void testToViewDto() {
        // given
        LocalDateTime viewedAt = LocalDateTime.now();
        ArticleView articleView = ArticleView.create(article, user, viewedAt);

        // when
        ArticleViewDto result = articleMapper.toViewDto(articleView, 5, 11);

        // then
        assertThat(result.viewedBy()).isEqualTo(userId);
        assertThat(result.articleId()).isEqualTo(articleId);
        assertThat(result.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(result.sourceUrl()).isEqualTo("https://example.com/article");
        assertThat(result.articleTitle()).isEqualTo("Test Article");
        assertThat(result.articlePublishedDate()).isEqualTo(publishDate);
        assertThat(result.articleSummary()).isEqualTo("Test Summary");
        assertThat(result.articleCommentCount()).isEqualTo(5);
        assertThat(result.articleViewCount()).isEqualTo(11);
    }

    @Test
    @DisplayName("null 입력 시 null을 반환한다")
    void testNullInput() {
        assertThat(articleMapper.toDto(null, true, null, null)).isNull();
        assertThat(articleMapper.toViewDto(null, null, null)).isNull();
    }
}