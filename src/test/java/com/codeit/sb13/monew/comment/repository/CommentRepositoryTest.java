package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.comment.domain.Comment;

import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchCondition;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchResult;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ARTICLE_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.COMMENT_DELETED;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.global.exception.comment.CommentSearchConditionInvalidException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
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
import org.springframework.data.domain.Sort.Direction;

import static org.assertj.core.api.Assertions.*;
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
    private CommentLikeRepository commentLikeRepository;

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



    // 댓글 목록 조회 ---------
    @Test
    @DisplayName("생성일 기준 오름차순으로 댓글 조회한다 - GREEN")
    void search_orderByCreatedAtAscending() {
        // given
        User requestUser = userRepository.saveAndFlush(new User("request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));

        Comment oldest = commentRepository.saveAndFlush(new Comment(article, writer, "오래된 댓글"));
        Comment newest = commentRepository.saveAndFlush(new Comment(article, writer, "최신 댓글"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(oldest.getId(), baseTime);
        updateCommentCreatedAt(newest.getId(), baseTime.plusMinutes(2));

        em.clear();

        CommentSearchCondition condition = new CommentSearchCondition(
                article.getId(),
                CommentOrderBy.CREATED_AT,
                Direction.ASC,
                null,
                null,
                10,
                requestUser.getId()
        );

        // when
        CommentSearchResult result = commentRepository.search(condition);

        // then
        Assertions.assertAll(
            ()->assertThat(result.rows())
                .hasSize(2)
                .extracting(CommentSearchProjection::content)
                .containsExactly("오래된 댓글", "최신 댓글"),
            ()->assertThat(result.hasNext()).isFalse(),
            ()->assertThat(result.totalElements()).isEqualTo(2)
        );
    }


    @Test
    @DisplayName("생성일 기준 내림차순으로 댓글 조회한다 - GREEN")
    void search_orderByCreatedAtDescending() {
        // given
        User requestUser = userRepository.saveAndFlush(new User("request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));

        Comment oldest = commentRepository.saveAndFlush(new Comment(article, writer, "오래된 댓글"));
        Comment newest = commentRepository.saveAndFlush(new Comment(article, writer, "최신 댓글"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(oldest.getId(), baseTime);
        updateCommentCreatedAt(newest.getId(), baseTime.plusMinutes(2));

        em.clear();

        CommentSearchCondition condition = new CommentSearchCondition(
            article.getId(),
            CommentOrderBy.CREATED_AT,
            Direction.DESC,
            null,
            null,
            10,
            requestUser.getId()
        );

        // when
        CommentSearchResult result = commentRepository.search(condition);

        // then
        Assertions.assertAll(
            ()->assertThat(result.rows())
                .hasSize(2)
                .extracting(CommentSearchProjection::content)
                .containsExactly("최신 댓글", "오래된 댓글"),
            ()->assertThat(result.hasNext()).isFalse(),
            ()->assertThat(result.totalElements()).isEqualTo(2)
        );
    }

    @Test
    @DisplayName("생성일 커서로 다음 페이지를 중복 없이 조회한다")
    void search_next_page_by_created_at_cursor() {
        User requestUser = userRepository.saveAndFlush(new User("request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));

        Comment first = commentRepository.saveAndFlush(new Comment(article, writer, "첫 번째 댓글"));
        Comment second = commentRepository.saveAndFlush(new Comment(article, writer, "두 번째 댓글"));
        Comment third = commentRepository.saveAndFlush(new Comment(article, writer, "세 번째 댓글"));
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(first.getId(), baseTime);
        updateCommentCreatedAt(second.getId(), baseTime.plusMinutes(1));
        updateCommentCreatedAt(third.getId(), baseTime.plusMinutes(2));
        em.clear();

        CommentSearchResult firstPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            null, null, 2, requestUser.getId()));
        CommentSearchProjection last = firstPage.rows().get(1);
        CommentSearchResult secondPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            last.id().toString(), last.createdAt(), 2, requestUser.getId()));

        Assertions.assertAll(
            () -> assertThat(firstPage.rows()).extracting(CommentSearchProjection::content)
                .containsExactly("첫 번째 댓글", "두 번째 댓글"),
            () -> assertThat(firstPage.hasNext()).isTrue(),
            () -> assertThat(secondPage.rows()).extracting(CommentSearchProjection::content)
                .containsExactly("세 번째 댓글"),
            () -> assertThat(secondPage.hasNext()).isFalse(),
            () -> assertThat(secondPage.totalElements()).isEqualTo(3L)
        );
    }

    @Test
    @DisplayName("cursor만 있어도 anchor 댓글 기준으로 다음 페이지를 조회할 수 있다")
    void search_allows_cursor_without_after() {
        User writer = userRepository.saveAndFlush(new User("partial-cursor-writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));
        Comment anchor = commentRepository.saveAndFlush(new Comment(article, writer, "첫 번째 댓글"));
        Comment next = commentRepository.saveAndFlush(new Comment(article, writer, "두 번째 댓글"));
        LocalDateTime anchorCreatedAt = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(anchor.getId(), anchorCreatedAt);
        updateCommentCreatedAt(next.getId(), anchorCreatedAt.plusMinutes(1));
        em.clear();

        CommentSearchResult result = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            anchor.getId().toString(), null, 10, null));

        assertThat(result.rows())
            .extracting(CommentSearchProjection::id)
            .containsExactly(next.getId());

        CommentSearchResult afterOnlyResult = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            null, anchorCreatedAt, 10, null));

        assertThat(afterOnlyResult.rows())
            .extracting(CommentSearchProjection::id)
            .containsExactly(anchor.getId(), next.getId());
    }

    @Test
    @DisplayName("articleId 없이도 cursor anchor를 기준으로 다음 페이지를 조회한다")
    void search_uses_cursor_anchor_when_article_id_is_absent() {
        User writer = userRepository.saveAndFlush(new User(
            "cursor-without-article-writer@email.com", "작성자", "testPassword?"));
        Article firstArticle = articleRepository.saveAndFlush(
            createArticle("첫 번째 기사", "기사 내용", "cursor-without-article-link-1"));
        Article secondArticle = articleRepository.saveAndFlush(
            createArticle("두 번째 기사", "기사 내용", "cursor-without-article-link-2"));
        Comment anchor = commentRepository.saveAndFlush(new Comment(firstArticle, writer, "첫 번째 댓글"));
        Comment next = commentRepository.saveAndFlush(new Comment(secondArticle, writer, "두 번째 댓글"));
        LocalDateTime anchorCreatedAt = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(anchor.getId(), anchorCreatedAt);
        updateCommentCreatedAt(next.getId(), anchorCreatedAt.plusMinutes(1));
        em.clear();

        CommentSearchResult result = commentRepository.search(new CommentSearchCondition(
            null, CommentOrderBy.CREATED_AT, Direction.ASC,
            anchor.getId().toString(), null, 10, null));

        assertThat(result.rows())
            .extracting(CommentSearchProjection::id)
            .containsExactly(next.getId());
    }

    @Test
    @DisplayName("생성일 내림차순 커서로 다음 페이지를 조회한다")
    void search_next_page_by_created_at_desc_cursor() {
        User requestUser = userRepository.saveAndFlush(new User("request-desc@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer-desc@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));
        Comment oldest = commentRepository.saveAndFlush(new Comment(article, writer, "오래된 댓글"));
        Comment middle = commentRepository.saveAndFlush(new Comment(article, writer, "중간 댓글"));
        Comment newest = commentRepository.saveAndFlush(new Comment(article, writer, "최신 댓글"));
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(oldest.getId(), baseTime);
        updateCommentCreatedAt(middle.getId(), baseTime.plusMinutes(1));
        updateCommentCreatedAt(newest.getId(), baseTime.plusMinutes(2));
        em.clear();

        CommentSearchResult firstPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.DESC,
            null, null, 2, requestUser.getId()));
        CommentSearchProjection last = firstPage.rows().get(1);
        CommentSearchResult secondPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.DESC,
            last.id().toString(), last.createdAt(), 2, requestUser.getId()));

        assertThat(secondPage.rows())
            .extracting(CommentSearchProjection::content)
            .containsExactly("오래된 댓글");
    }

    @Test
    @DisplayName("생성일이 같은 댓글도 id 커서로 다음 페이지를 조회한다")
    void search_next_page_with_same_created_at_uses_id_tiebreaker() {
        User requestUser = userRepository.saveAndFlush(new User("request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));
        Comment first = commentRepository.saveAndFlush(new Comment(article, writer, "동일 시각 댓글 1"));
        Comment second = commentRepository.saveAndFlush(new Comment(article, writer, "동일 시각 댓글 2"));
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(first.getId(), createdAt);
        updateCommentCreatedAt(second.getId(), createdAt);
        em.clear();

        CommentSearchResult firstPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            null, null, 1, requestUser.getId()));
        CommentSearchProjection last = firstPage.rows().get(0);
        CommentSearchResult secondPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            last.id().toString(), last.createdAt(), 1, requestUser.getId()));

        assertThat(secondPage.rows())
            .hasSize(1)
            .extracting(CommentSearchProjection::id)
            .doesNotContain(last.id());
    }

    @Test
    @DisplayName("좋아요 수 기준 오름차순으로 댓글 조회한다 - GREEN")
    void search_orderByLikeCountAscending() {
        // given
        User requestUser = userRepository.saveAndFlush(new User("request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer@email.com", "작성자", "testPassword?"));
        User likedUser1 = userRepository.saveAndFlush(new User("liked1@email.com", "좋아요한 사용자1", "testPassword!"));
        User likedUser2 = userRepository.saveAndFlush(new User("liked2@email.com", "좋아요한 사용자2", "testPassword@"));

        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));

        Comment zeroLikes = commentRepository.saveAndFlush(new Comment(article, writer, "좋아요 없는 댓글"));
        Comment sevenLikes = commentRepository.saveAndFlush(new Comment(article, writer, "좋아요 있는 댓글"));
        commentLikeRepository.saveAndFlush(CommentLike.builder()
            .comment(sevenLikes)
            .likedBy(likedUser2)
            .build());

        em.clear();

        CommentSearchCondition condition = new CommentSearchCondition(
            article.getId(),
            CommentOrderBy.LIKE_COUNT,
            Direction.ASC,
            null,
            null,
            10,
            likedUser1.getId()
        );

        // when
        CommentSearchResult result = commentRepository.search(condition);

        // then
        Assertions.assertAll(
            ()->assertThat(result.rows())
                .hasSize(2)
                .extracting(CommentSearchProjection::content)
                .containsExactly("좋아요 없는 댓글", "좋아요 있는 댓글"),
            ()->assertThat(result.rows()).extracting(CommentSearchProjection::likeCount).containsExactly(0L, 1L),
            ()->assertThat(result.hasNext()).isFalse(),
            ()->assertThat(result.totalElements()).isEqualTo(2L)
        );
    }


    @Test
    @DisplayName("좋아요 수 기준 내림차순으로 댓글 조회한다 - RED")
    void search_orderByLikeCountDescending() {
        // given
        User requestUser = userRepository.saveAndFlush(new User("request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("writer@email.com", "작성자", "testPassword?"));
        User likedUser1 = userRepository.saveAndFlush(new User("liked1@email.com", "좋아요한 사용자1", "testPassword!"));
        User likedUser2 = userRepository.saveAndFlush(new User("liked2@email.com", "좋아요한 사용자2", "testPassword@"));

        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));

        Comment zeroLikes = commentRepository.saveAndFlush(new Comment(article, writer, "좋아요 없는 댓글"));
        Comment sevenLikes = commentRepository.saveAndFlush(new Comment(article, writer, "좋아요 있는 댓글"));
        commentLikeRepository.saveAndFlush(CommentLike.builder()
            .comment(sevenLikes)
            .likedBy(likedUser2)
            .build());

        em.clear();

        CommentSearchCondition condition = new CommentSearchCondition(
            article.getId(),
            CommentOrderBy.LIKE_COUNT,
            Direction.DESC,
            null,
            null,
            10,
            requestUser.getId()
        );

        // when
        CommentSearchResult result = commentRepository.search(condition);

        // then
        Assertions.assertAll(
            ()->assertThat(result.rows())
                .hasSize(2)
                .extracting(CommentSearchProjection::content)
                .containsExactly("좋아요 있는 댓글", "좋아요 없는 댓글"),
            ()->assertThat(result.rows()).extracting(CommentSearchProjection::likeCount).containsExactly(1L, 0L),
            ()->assertThat(result.hasNext()).isFalse(),
            ()->assertThat(result.totalElements()).isEqualTo(2L)
        );
    }

    @Test
    @DisplayName("좋아요 수 커서로 오름차순과 내림차순 다음 페이지를 조회한다")
    void search_next_page_by_like_count_cursor() {
        User requestUser = userRepository.saveAndFlush(new User("cursor-request@email.com", "요청자", "testPassword!"));
        User writer = userRepository.saveAndFlush(new User("cursor-writer@email.com", "작성자", "testPassword?"));
        User liker = userRepository.saveAndFlush(new User("cursor-liker@email.com", "좋아요 사용자", "testPassword@"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));
        Comment zeroLikes = commentRepository.saveAndFlush(new Comment(article, writer, "좋아요 없는 댓글"));
        Comment oneLike = commentRepository.saveAndFlush(new Comment(article, writer, "좋아요 한 개 댓글"));
        commentLikeRepository.saveAndFlush(CommentLike.builder().comment(oneLike).likedBy(liker).build());
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 25, 12, 0);
        updateCommentCreatedAt(zeroLikes.getId(), baseTime);
        updateCommentCreatedAt(oneLike.getId(), baseTime.plusMinutes(1));
        em.clear();

        CommentSearchResult firstAscendingPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.LIKE_COUNT, Direction.ASC,
            null, null, 1, requestUser.getId()));
        CommentSearchProjection ascendingCursor = firstAscendingPage.rows().get(0);
        CommentSearchResult nextAscendingPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.LIKE_COUNT, Direction.ASC,
            ascendingCursor.id().toString(), ascendingCursor.createdAt(), 1, requestUser.getId()));

        CommentSearchResult firstDescendingPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.LIKE_COUNT, Direction.DESC,
            null, null, 1, requestUser.getId()));
        CommentSearchProjection descendingCursor = firstDescendingPage.rows().get(0);
        CommentSearchResult nextDescendingPage = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.LIKE_COUNT, Direction.DESC,
            descendingCursor.id().toString(), descendingCursor.createdAt(), 1, requestUser.getId()));

        Assertions.assertAll(
            () -> assertThat(nextAscendingPage.rows()).extracting(CommentSearchProjection::content)
                .containsExactly("좋아요 한 개 댓글"),
            () -> assertThat(nextDescendingPage.rows()).extracting(CommentSearchProjection::content)
                .containsExactly("좋아요 없는 댓글")
        );
    }

    @Test
    @DisplayName("요청자 ID가 없으면 likedByMe를 false로 반환한다")
    void search_returns_false_for_liked_by_me_when_request_user_is_absent() {
        User writer = userRepository.saveAndFlush(new User("anonymous-writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));
        commentRepository.saveAndFlush(new Comment(article, writer, "댓글"));
        em.clear();

        CommentSearchResult result = commentRepository.search(new CommentSearchCondition(
            article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
            null, null, 10, null));

        // allSatisfy는 result.rows()가 비어 있어도 통과하기 때문에 댓글 수나 댓글 내용을 먼저 검증한 뒤 likedByMe()를 확인하는 방향으로 개선
        assertThat(result.rows()).hasSize(1).allSatisfy(row->assertThat(row.likedByMe()).isFalse());
    }

    @Test
    @DisplayName("articleId가 없으면 활성화된 댓글 전체를 조회한다")
    void search_without_article_id_returns_comments_from_all_articles() {
        User writer = userRepository.saveAndFlush(new User(
            "all-comments-writer@email.com", "작성자", "testPassword!"));
        Article firstArticle = articleRepository.saveAndFlush(
            createArticle("첫 번째 기사", "기사 내용", "all-comments-link-1"));
        Article secondArticle = articleRepository.saveAndFlush(
            createArticle("두 번째 기사", "기사 내용", "all-comments-link-2"));
        commentRepository.saveAndFlush(new Comment(firstArticle, writer, "첫 번째 기사 댓글"));
        commentRepository.saveAndFlush(new Comment(secondArticle, writer, "두 번째 기사 댓글"));
        em.clear();

        CommentSearchResult result = commentRepository.search(new CommentSearchCondition(
            null, CommentOrderBy.CREATED_AT, Direction.ASC, null, null, 10, null));

        assertThat(result.rows())
            .extracting(CommentSearchProjection::content)
            .containsExactlyInAnyOrder("첫 번째 기사 댓글", "두 번째 기사 댓글");
        assertThat(result.totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("잘못된 커서 형식이면 목록 조회에 실패한다")
    void search_fails_when_cursor_format_is_invalid() {
        User writer = userRepository.saveAndFlush(new User("invalid-cursor-writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(createArticle("테스트 기사", "테스트 기사 내용", "testLink"));
        Comment comment = commentRepository.saveAndFlush(new Comment(article, writer, "댓글"));
        em.clear();

        assertThat(catchThrowable(() -> commentRepository.search(
            new CommentSearchCondition(article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
                "invalid-date", LocalDateTime.now(), 10, null))))
            .isInstanceOf(CommentSearchConditionInvalidException.class);
        assertThat(catchThrowable(() -> commentRepository.search(
            new CommentSearchCondition(article.getId(), CommentOrderBy.LIKE_COUNT, Direction.ASC,
                "invalid-like-count", LocalDateTime.now(), 10, null))))
            .isInstanceOf(CommentSearchConditionInvalidException.class);
    }

    @Test
    @DisplayName("존재하지 않는 UUID cursor라면 목록 조회에 실패한다")
    void search_fails_when_cursor_anchor_does_not_exist() {
        User writer = userRepository.saveAndFlush(new User(
            "missing-cursor-writer@email.com", "작성자", "testPassword?"));
        Article article = articleRepository.saveAndFlush(
            createArticle("테스트 기사", "테스트 기사 내용", "missing-cursor-link"));
        commentRepository.saveAndFlush(new Comment(article, writer, "댓글"));
        em.clear();

        assertThat(catchThrowable(() -> commentRepository.search(
            new CommentSearchCondition(article.getId(), CommentOrderBy.CREATED_AT, Direction.ASC,
                UUID.randomUUID().toString(), null, 10, null))))
            .isInstanceOf(CommentSearchConditionInvalidException.class);
    }


    @Test
    @DisplayName("활성화된 댓글만 조건부로 논리 삭제하고, 삭제 재시도는 0건을 반환한다")
    void softDeleteIfNotDeleted_updatesOnlyActiveComment() {
        User writer = userRepository.saveAndFlush(new User(
            "soft-delete-writer@email.com", "작성자", "testPassword!"));
        Article article = articleRepository.saveAndFlush(
            createArticle("테스트 기사", "테스트 기사 내용", "soft-delete-link"));
        Comment comment = commentRepository.saveAndFlush(new Comment(article, writer, "삭제할 댓글"));

        LocalDateTime deletedAt = LocalDateTime.of(2026, 8, 26, 12, 0);
        int firstUpdatedCount = commentRepository.softDeleteIfNotDeleted(comment.getId(), deletedAt);
        int secondUpdatedCount = commentRepository.softDeleteIfNotDeleted(
            comment.getId(), LocalDateTime.of(2026, 8, 26, 12, 1));
        em.clear();

        assertThat(firstUpdatedCount).isEqualTo(1);
        assertThat(secondUpdatedCount).isZero();
        assertThat(commentRepository.findActiveById(comment.getId())).isEmpty();
        Comment deletedComment = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(deletedComment.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(deletedComment.getUpdatedAt()).isEqualTo(deletedAt);
        assertThat(deletedComment.getVisibilityStatus()).isEqualTo(COMMENT_DELETED);
    }

    @Test
    @DisplayName("이미 다른 사유로 노출 상태가 갱신된 댓글도 자신의 논리 삭제 시 COMMENT_DELETED로 덮어쓴다")
    void softDeleteIfNotDeleted_overwritesExistingNonActiveVisibilityStatus() {
        User writer = userRepository.saveAndFlush(new User(
            "soft-delete-overwrite-writer@email.com", "작성자", "testPassword!"));
        Article article = articleRepository.saveAndFlush(
            createArticle("테스트 기사", "테스트 기사 내용", "soft-delete-overwrite-link"));
        Comment comment = commentRepository.saveAndFlush(new Comment(article, writer, "삭제할 댓글"));
        em.getEntityManager()
            .createQuery("UPDATE Comment c SET c.visibilityStatus = :status WHERE c.id = :id")
            .setParameter("status", ARTICLE_DELETED)
            .setParameter("id", comment.getId())
            .executeUpdate();
        em.clear();

        LocalDateTime deletedAt = LocalDateTime.of(2026, 8, 26, 12, 0);
        int updatedCount = commentRepository.softDeleteIfNotDeleted(comment.getId(), deletedAt);
        em.clear();

        assertThat(updatedCount).isEqualTo(1);
        Comment deletedComment = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(deletedComment.getVisibilityStatus()).isEqualTo(COMMENT_DELETED);
    }

    @Test
    @DisplayName("댓글 물리 삭제를 위한 대상 조회 시 논리 삭제된 댓글도 포함한다")
    void findForHardDeleteById_includesSoftDeletedComment() {
        User writer = userRepository.saveAndFlush(new User(
            "hard-delete-writer@email.com", "작성자", "testPassword!"));
        Article article = articleRepository.saveAndFlush(
            createArticle("테스트 기사", "테스트 기사 내용", "hard-delete-link"));
        Comment comment = commentRepository.saveAndFlush(new Comment(article, writer, "정리할 댓글"));
        comment.softDelete();
        commentRepository.saveAndFlush(comment);
        em.clear();

        assertThat(commentRepository.findForHardDeleteById(comment.getId())).isPresent();
    }
}
