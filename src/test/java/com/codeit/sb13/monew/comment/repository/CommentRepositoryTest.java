package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("작성한 댓글이 없으면 빈 목록 반환")
    void returns_empty_list_when_user_has_no_comments() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        List<RecentCommentActivityProjection> projections = commentRepository.findRecentCommentActivities(userId);

        // then
        assertThat(projections).isEmpty();
    }

    @Test
    @DisplayName("사용자가 작성한 댓글을 최신 작성순으로 반환")
    void returns_user_comments_ordered_by_created_at_desc() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        User otherUser = new User("otherTest@email.com", "otherTestNickname", "otherTestPassword");
        userRepository.saveAndFlush(targetUser);
        userRepository.saveAndFlush(otherUser);

        Article article = articleRepository.saveAndFlush(createArticle("testTitle", "testContent", "link"));
        Comment oldestComment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment1"));
        Comment middleComment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment2"));
        Comment newestComment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment3"));
        commentRepository.saveAndFlush(new Comment(article, otherUser, "testComment4"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        updateCommentCreatedAt(oldestComment.getId(), baseTime.minusMinutes(2));
        updateCommentCreatedAt(middleComment.getId(), baseTime.minusMinutes(1));
        updateCommentCreatedAt(newestComment.getId(), baseTime);

        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId());

        // then
        assertThat(recentCommentActivities)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("testComment3", "testComment2", "testComment1");
    }

    @Test
    @DisplayName("10건 초과 시 최신 10건만 반환")
    void returns_latest_10_user_comments_when_more_than_10_comments_exist() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        User otherUser = new User("otherTest@email.com", "otherTestNickname", "otherTestPassword");
        userRepository.saveAndFlush(targetUser);
        userRepository.saveAndFlush(otherUser);

        Article article = articleRepository.saveAndFlush(createArticle("testTitle", "testContent", "link"));
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        for (int index = 1; index <= 11; index++) {
            Comment comment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment" + index));
            updateCommentCreatedAt(comment.getId(), baseTime.minusMinutes(11L - index));
        }

        Comment otherUserComment = commentRepository.saveAndFlush(new Comment(article, otherUser, "otherTestComment"));
        updateCommentCreatedAt(otherUserComment.getId(), baseTime.plusMinutes(1));

        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId());

        // then
        assertThat(recentCommentActivities)
                .hasSize(10)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("testComment11", "testComment10", "testComment9", "testComment8", "testComment7", "testComment6", "testComment5", "testComment4", "testComment3", "testComment2");
    }

    @Test
    @DisplayName("작성 시각이 같으면 댓글 ID 내림차순으로 보조 정렬한다")
    void returns_user_comments_ordered_by_created_at_desc_and_id_desc_when_created_at_ties() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        userRepository.saveAndFlush(targetUser);

        Article article = articleRepository.saveAndFlush(createArticle("testTitle", "testContent", "link"));
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 8, 20, 10, 0);

        for (int index = 1; index <= 11; index++) {
            Comment comment = commentRepository.saveAndFlush(new Comment(article, targetUser, "sameCreatedAtComment" + index));
            updateCommentCreatedAt(comment.getId(), sameCreatedAt);
        }

        em.clear();

        // when
        List<UUID> expectedIds = findExpectedCommentIdsByNativeOrder(targetUser.getId());
        List<UUID> resultIds = commentRepository.findRecentCommentActivities(targetUser.getId())
                .stream()
                .map(RecentCommentActivityProjection::id)
                .toList();

        // then
        assertThat(resultIds).hasSize(10);
        assertThat(resultIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("삭제된 사용자 댓글 제외")
    void returns_empty_when_user_is_soft_deleted() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        userRepository.saveAndFlush(targetUser);

        Article article = articleRepository.saveAndFlush(createArticle("testTitle", "testContent", "link"));
        commentRepository.saveAndFlush(new Comment(article, targetUser, "deletedUserComment"));

        targetUser.softDelete();
        userRepository.saveAndFlush(targetUser);
        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId());

        // then
        assertThat(recentCommentActivities).isEmpty();
    }

    @Test
    @DisplayName("삭제된 댓글은 최근 작성 댓글에서 제외")
    void excludes_soft_deleted_comments_from_recent_comment_activities() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        userRepository.saveAndFlush(targetUser);

        Article article = articleRepository.saveAndFlush(createArticle("testTitle", "testContent", "link"));
        Comment oldestComment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment1"));
        Comment middleComment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment2"));
        Comment newestComment = commentRepository.saveAndFlush(new Comment(article, targetUser, "testComment3"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        updateCommentCreatedAt(oldestComment.getId(), baseTime.minusMinutes(2));
        updateCommentCreatedAt(middleComment.getId(), baseTime.minusMinutes(1));
        updateCommentCreatedAt(newestComment.getId(), baseTime);

        middleComment.softDelete();
        commentRepository.saveAndFlush(middleComment);

        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId());

        // then
        assertThat(recentCommentActivities)
                .hasSize(2)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("testComment3", "testComment1");
    }

    @Test
    @DisplayName("삭제된 기사의 댓글은 최근 작성 댓글에서 제외")
    void excludes_comments_on_soft_deleted_articles_from_recent_comment_activities() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        userRepository.saveAndFlush(targetUser);

        Article activeArticle = articleRepository.saveAndFlush(createArticle("activeTitle", "activeContent", "activeLink"));
        Article deletedArticle = articleRepository.saveAndFlush(createArticle("deletedTitle", "deletedContent", "deletedLink"));

        Comment activeArticleComment = commentRepository.saveAndFlush(new Comment(activeArticle, targetUser, "activeArticleComment"));
        Comment deletedArticleComment = commentRepository.saveAndFlush(new Comment(deletedArticle, targetUser, "deletedArticleComment"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        updateCommentCreatedAt(activeArticleComment.getId(), baseTime);
        updateCommentCreatedAt(deletedArticleComment.getId(), baseTime.plusMinutes(1));

        deletedArticle.softDelete();
        articleRepository.saveAndFlush(deletedArticle);

        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId());

        // then
        assertThat(recentCommentActivities)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("activeArticleComment");
    }

    private void updateCommentCreatedAt(UUID commentId, LocalDateTime createdAt) {
        em.getEntityManager()
                .createNativeQuery("UPDATE comments SET created_at = ? WHERE id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, commentId)
                .executeUpdate();
    }

    private List<UUID> findExpectedCommentIdsByNativeOrder(UUID userId) {
        List<?> rows = em.getEntityManager()
                .createNativeQuery("""
                        SELECT C.id
                        FROM comments C
                            JOIN users U ON U.id = C.user_id
                            JOIN articles A ON A.id = C.article_id
                        WHERE C.user_id = :userId
                            AND U.deleted_at IS NULL
                            AND C.deleted_at IS NULL
                            AND A.deleted_at IS NULL
                        ORDER BY C.created_at DESC, C.id DESC
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

    private Article createArticle(String title, String summary, String link) {
        return Article.create(title, summary, link, LocalDateTime.now(), ArticleSource.NAVER);
    }
}
