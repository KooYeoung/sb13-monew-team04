package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.dto.RecentArticleViewActivityProjection;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class ArticleViewRepositoryTest {

    @Autowired
    private ArticleViewRepository articleViewRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("기사 조회수는 ACTIVE 조회 기록만 집계한다")
    void countsOnlyActiveArticleViews() {
        // given
        Article article = saveArticle("count", LocalDateTime.of(2026, 8, 22, 10, 0));
        saveArticleView(article, saveUser("active-viewer"), LocalDateTime.of(2026, 8, 22, 11, 0));
        ArticleView hiddenView = saveArticleView(
                article,
                saveUser("hidden-viewer"),
                LocalDateTime.of(2026, 8, 22, 12, 0)
        );
        updateArticleViewVisibilityStatus(hiddenView.getId(), ActivityVisibilityStatus.USER_DELETED);
        flushAndClear();

        // when
        long result = articleViewRepository.countActiveByArticleId(article.getId());

        // then
        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("조회한 뉴스 기사가 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenUserHasNoArticleViews() {
        // given
        User user = saveUser("no-view");

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(user.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조회 이력이 여러 개이면 최신 조회순으로 반환한다")
    void returnsArticleViewsOrderedByViewedAtDesc() {
        // given
        User targetUser = saveUser("target");
        User otherUser = saveUser("other");
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 22, 10, 0);

        Article oldestArticle = saveArticle("oldest", baseTime.minusDays(3));
        Article middleArticle = saveArticle("middle", baseTime.minusDays(2));
        Article newestArticle = saveArticle("newest", baseTime.minusDays(1));
        Article otherUserArticle = saveArticle("other-user", baseTime);

        saveArticleView(oldestArticle, targetUser, baseTime.minusMinutes(2));
        saveArticleView(middleArticle, targetUser, baseTime.minusMinutes(1));
        saveArticleView(newestArticle, targetUser, baseTime);
        saveArticleView(otherUserArticle, otherUser, baseTime.plusMinutes(1));
        flushAndClear();

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(targetUser.getId());

        // then
        assertThat(result)
                .extracting(RecentArticleViewActivityProjection::articleTitle)
                .containsExactly("newest", "middle", "oldest");
    }

    @Test
    @DisplayName("조회 이력이 10건을 초과하면 최신 10건만 반환한다")
    void returnsLatest10ArticleViewsWhenMoreThan10Exist() {
        // given
        User targetUser = saveUser("target");
        User otherUser = saveUser("other");
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 22, 10, 0);

        for (int index = 1; index <= 11; index++) {
            Article article = saveArticle("article" + index, baseTime.minusDays(index));
            saveArticleView(article, targetUser, baseTime.minusMinutes(11L - index));
        }

        Article otherUserArticle = saveArticle("other-user", baseTime);
        saveArticleView(otherUserArticle, otherUser, baseTime.plusMinutes(1));
        flushAndClear();

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(targetUser.getId());

        // then
        assertThat(result)
                .hasSize(10)
                .extracting(RecentArticleViewActivityProjection::articleTitle)
                .containsExactly(
                        "article11", "article10", "article9", "article8", "article7",
                        "article6", "article5", "article4", "article3", "article2");
    }

    @Test
    @DisplayName("조회 시각이 같으면 조회 기록 ID 내림차순으로 보조 정렬한다")
    void returnsArticleViewsOrderedByViewedAtDescAndIdDescWhenViewedAtTies() {
        // given
        User targetUser = saveUser("target");
        LocalDateTime sameViewedAt = LocalDateTime.of(2026, 8, 22, 10, 0);

        for (int index = 1; index <= 11; index++) {
            Article article = saveArticle("same-viewed-at-" + index, sameViewedAt.minusDays(index));
            saveArticleView(article, targetUser, sameViewedAt);
        }
        flushAndClear();

        // when
        List<UUID> expectedIds = findExpectedArticleViewIdsByNativeOrder(targetUser.getId());
        List<UUID> resultIds = articleViewRepository.findRecentArticleViewActivities(targetUser.getId())
                .stream()
                .map(RecentArticleViewActivityProjection::id)
                .toList();

        // then
        assertThat(resultIds).hasSize(10);
        assertThat(resultIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("USER_DELETED 조회 이력은 제외한다")
    void excludesUserDeletedArticleViews() {
        // given
        User targetUser = saveUser("deleted-user");
        Article article = saveArticle("article", LocalDateTime.of(2026, 8, 22, 10, 0));
        ArticleView hiddenView = saveArticleView(
                article,
                targetUser,
                LocalDateTime.of(2026, 8, 22, 11, 0)
        );

        targetUser.softDelete();
        userRepository.saveAndFlush(targetUser);
        updateArticleViewVisibilityStatus(hiddenView.getId(), ActivityVisibilityStatus.USER_DELETED);
        flushAndClear();

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(targetUser.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ARTICLE_DELETED 조회 이력은 제외한다")
    void excludesArticleDeletedArticleViews() {
        // given
        User targetUser = saveUser("target");
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 22, 10, 0);

        Article activeArticle = saveArticle("active", baseTime);
        Article deletedArticle = saveArticle("deleted", baseTime.plusMinutes(1));
        saveArticleView(activeArticle, targetUser, baseTime);
        ArticleView hiddenView = saveArticleView(
                deletedArticle,
                targetUser,
                baseTime.plusMinutes(1)
        );

        deletedArticle.softDelete();
        articleRepository.saveAndFlush(deletedArticle);
        updateArticleViewVisibilityStatus(hiddenView.getId(), ActivityVisibilityStatus.ARTICLE_DELETED);
        flushAndClear();

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(targetUser.getId());

        // then
        assertThat(result)
                .extracting(RecentArticleViewActivityProjection::articleTitle)
                .containsExactly("active");
    }

    @Test
    @DisplayName("같은 기사를 다시 조회하면 중복 없이 최신 조회 시각으로 반환한다")
    void returnsSingleArticleViewUpdatedWithLatestViewedAt() {
        // given
        User targetUser = saveUser("target");
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 22, 10, 0);

        Article reViewedArticle = saveArticle("reviewed", baseTime);
        Article otherArticle = saveArticle("other", baseTime.minusMinutes(1));
        ArticleView reViewed = saveArticleView(reViewedArticle, targetUser, baseTime.minusMinutes(10));
        saveArticleView(otherArticle, targetUser, baseTime);

        LocalDateTime latestViewedAt = baseTime.plusMinutes(1);
        reViewed.updateViewedAt(latestViewedAt);
        articleViewRepository.saveAndFlush(reViewed);
        flushAndClear();

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(targetUser.getId());

        // then
        assertThat(result)
                .hasSize(2)
                .extracting(RecentArticleViewActivityProjection::articleTitle)
                .containsExactly("reviewed", "other");
        assertThat(result.get(0).viewedAt()).isEqualTo(latestViewedAt);
    }

    @Test
    @DisplayName("최근 본 뉴스 기사 projection 필드와 집계 값을 반환한다")
    void returnsProjectionFieldsAndCounts() {
        // given
        User targetUser = saveUser("target");
        User activeViewer = saveUser("active-viewer");
        User deletedViewer = saveUser("deleted-viewer");
        User activeCommenter = saveUser("active-commenter");
        User deletedCommenter = saveUser("deleted-commenter");

        LocalDateTime articlePublishedAt = LocalDateTime.of(2026, 8, 22, 9, 0);
        LocalDateTime targetViewedAt = LocalDateTime.of(2026, 8, 22, 10, 0);
        Article article = saveArticle("projection", articlePublishedAt);

        ArticleView targetView = saveArticleView(article, targetUser, targetViewedAt);
        saveArticleView(article, activeViewer, targetViewedAt.minusMinutes(1));
        ArticleView deletedViewerView =
                saveArticleView(article, deletedViewer, targetViewedAt.minusMinutes(2));

        Comment activeComment = new Comment(article, activeCommenter, "active comment");
        Comment deletedComment = new Comment(article, activeCommenter, "deleted comment");
        Comment deletedUserComment = new Comment(article, deletedCommenter, "deleted user comment");
        commentRepository.saveAndFlush(activeComment);
        commentRepository.saveAndFlush(deletedComment);
        commentRepository.saveAndFlush(deletedUserComment);

        deletedComment.softDelete();
        commentRepository.saveAndFlush(deletedComment);
        deletedViewer.softDelete();
        userRepository.saveAndFlush(deletedViewer);
        deletedCommenter.softDelete();
        userRepository.saveAndFlush(deletedCommenter);
        updateArticleViewVisibilityStatus(
                deletedViewerView.getId(),
                ActivityVisibilityStatus.USER_DELETED
        );
        updateCommentVisibilityStatus(
                deletedComment.getId(),
                ActivityVisibilityStatus.COMMENT_DELETED
        );
        updateCommentVisibilityStatus(
                deletedUserComment.getId(),
                ActivityVisibilityStatus.USER_DELETED
        );
        flushAndClear();

        // when
        List<RecentArticleViewActivityProjection> result =
                articleViewRepository.findRecentArticleViewActivities(targetUser.getId());

        // then
        assertThat(result).hasSize(1);
        RecentArticleViewActivityProjection projection = result.get(0);
        assertThat(projection.id()).isEqualTo(targetView.getId());
        assertThat(projection.viewedBy()).isEqualTo(targetUser.getId());
        assertThat(projection.viewedAt()).isEqualTo(targetViewedAt);
        assertThat(projection.articleId()).isEqualTo(article.getId());
        assertThat(projection.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(projection.sourceUrl()).isEqualTo("https://example.com/articles/projection");
        assertThat(projection.articleTitle()).isEqualTo("projection");
        assertThat(projection.articlePublishedDate()).isEqualTo(articlePublishedAt);
        assertThat(projection.articleSummary()).isEqualTo("summary-projection");
        assertThat(projection.articleCommentCount()).isEqualTo(1L);
        assertThat(projection.articleViewCount()).isEqualTo(2L);
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(User.builder()
                .email(name + "-" + UUID.randomUUID() + "@test.com")
                .nickname(name)
                .password("password")
                .build());
    }

    private Article saveArticle(String title, LocalDateTime publishedAt) {
        return articleRepository.saveAndFlush(Article.create(
                title,
                "summary-" + title,
                "https://example.com/articles/" + title,
                publishedAt,
                ArticleSource.NAVER
        ));
    }

    private ArticleView saveArticleView(Article article, User user, LocalDateTime viewedAt) {
        return articleViewRepository.saveAndFlush(ArticleView.create(article, user, viewedAt));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private void updateArticleViewVisibilityStatus(
            UUID articleViewId,
            ActivityVisibilityStatus status
    ) {
        em.flush();
        em.getEntityManager()
                .createQuery("""
                        UPDATE ArticleView at
                        SET at.visibilityStatus = :status
                        WHERE at.id = :articleViewId
                        """)
                .setParameter("status", status)
                .setParameter("articleViewId", articleViewId)
                .executeUpdate();
    }

    private void updateCommentVisibilityStatus(
            UUID commentId,
            ActivityVisibilityStatus status
    ) {
        em.flush();
        em.getEntityManager()
                .createQuery("""
                        UPDATE Comment c
                        SET c.visibilityStatus = :status
                        WHERE c.id = :commentId
                        """)
                .setParameter("status", status)
                .setParameter("commentId", commentId)
                .executeUpdate();
    }

    private List<UUID> findExpectedArticleViewIdsByNativeOrder(UUID userId) {
        List<?> rows = em.getEntityManager()
                .createNativeQuery("""
                        SELECT id
                        FROM article_views
                        WHERE user_id = :userId
                        ORDER BY viewed_at DESC, id DESC
                        LIMIT 10
                        """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .map(this::toUuid)
                .toList();
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String string) {
            return UUID.fromString(string);
        }
        if (value instanceof byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        throw new IllegalArgumentException("Unsupported UUID value type: " + value.getClass());
    }
}
