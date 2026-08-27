package com.codeit.sb13.monew.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentLikeActivityProjection;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.user.domain.User;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class CommentLikeRepositoryTest {

  @Autowired
  private CommentLikeRepository commentLikeRepository;

  @Autowired
  private CommentRepository commentRepository;

  @Autowired
  private TestEntityManager em;

  @Test
  @DisplayName("좋아요한 댓글이 없으면 빈 목록을 반환한다")
  void findRecentCommentLikeActivity_noLikes_returnsEmptyList() {
    User requester = persistUser("requester");
    em.flush();
    em.clear();

    List<RecentCommentLikeActivityProjection> results =
        commentLikeRepository.findRecentCommentLikeActivity(requester.getId());

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("좋아요한 댓글을 좋아요 생성일 기준 최신순으로 최대 10건 반환한다")
  void findRecentCommentLikeActivity_returnsLatest10OrderedByLikeCreatedAtDesc() {
    User requester = persistUser("requester");
    User writer = persistUser("writer");
    Article article = persistArticle("article");
    LocalDateTime baseTime = LocalDateTime.of(2026, 8, 22, 10, 0);
    List<CommentLike> likes = new ArrayList<>();

    for (int i = 0; i < 11; i++) {
      Comment comment = persistComment(article, writer, "comment-" + i);
      likes.add(persistCommentLike(comment, requester));
    }
    em.flush();

    for (int i = 0; i < likes.size(); i++) {
      updateCommentLikeCreatedAt(likes.get(i), baseTime.plusMinutes(i));
    }
    em.clear();

    List<RecentCommentLikeActivityProjection> results =
        commentLikeRepository.findRecentCommentLikeActivity(requester.getId());

    assertThat(results).hasSize(10);
    assertThat(results)
        .extracting(RecentCommentLikeActivityProjection::commentContent)
        .containsExactly(
            "comment-10",
            "comment-9",
            "comment-8",
            "comment-7",
            "comment-6",
            "comment-5",
            "comment-4",
            "comment-3",
            "comment-2",
            "comment-1"
        );
  }

  @Test
  @DisplayName("좋아요 생성일이 같으면 좋아요 ID 내림차순으로 보조 정렬한다")
  void findRecentCommentLikeActivity_sameCreatedAt_ordersByLikeIdDesc() {
    User requester = persistUser("requester");
    User writer = persistUser("writer");
    Article article = persistArticle("article");
    LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 8, 22, 10, 0);
    List<CommentLike> likes = new ArrayList<>();

    for (int i = 0; i < 11; i++) {
      Comment comment = persistComment(article, writer, "same-time-comment-" + i);
      likes.add(persistCommentLike(comment, requester));
    }
    em.flush();

    for (CommentLike like : likes) {
      updateCommentLikeCreatedAt(like, sameCreatedAt);
    }
    em.clear();

    List<UUID> expectedIds = findExpectedLikeIdsByNativeOrder(requester.getId());
    List<UUID> resultIds = commentLikeRepository.findRecentCommentLikeActivity(requester.getId())
        .stream()
        .map(RecentCommentLikeActivityProjection::id)
        .toList();

    assertThat(resultIds).hasSize(10);
    assertThat(resultIds).containsExactlyElementsOf(expectedIds);
  }

  @Test
  @DisplayName("논리삭제된 사용자, 댓글, 기사와 관련된 좋아요 활동은 제외한다")
  void findRecentCommentLikeActivity_excludesSoftDeletedRows() {
    User requester = persistUser("requester");
    User writer = persistUser("writer");
    Article article = persistArticle("article");
    Comment validComment = persistComment(article, writer, "valid comment");
    persistCommentLike(validComment, requester);

    User deletedWriter = persistUser("deleted-writer");
    deletedWriter.softDelete();
    Comment commentByDeletedWriter = persistComment(article, deletedWriter, "deleted writer comment");
    persistCommentLike(commentByDeletedWriter, requester);

    Comment deletedComment = persistComment(article, writer, "deleted comment");
    deletedComment.softDelete();
    persistCommentLike(deletedComment, requester);

    Article deletedArticle = persistArticle("deleted article");
    deletedArticle.softDelete();
    Comment commentOnDeletedArticle = persistComment(deletedArticle, writer, "deleted article comment");
    persistCommentLike(commentOnDeletedArticle, requester);

    User deletedRequester = persistUser("deleted-requester");
    deletedRequester.softDelete();
    Comment likedByDeletedRequester = persistComment(article, writer, "liked by deleted requester");
    persistCommentLike(likedByDeletedRequester, deletedRequester);

    em.flush();
    em.clear();

    List<RecentCommentLikeActivityProjection> activeRequesterResults =
        commentLikeRepository.findRecentCommentLikeActivity(requester.getId());
    List<RecentCommentLikeActivityProjection> deletedRequesterResults =
        commentLikeRepository.findRecentCommentLikeActivity(deletedRequester.getId());

    assertThat(activeRequesterResults)
        .extracting(RecentCommentLikeActivityProjection::commentContent)
        .containsExactly("valid comment");
    assertThat(deletedRequesterResults).isEmpty();
  }

  @Test
  @DisplayName("좋아요 취소로 물리삭제된 좋아요는 조회되지 않는다")
  void findRecentCommentLikeActivity_excludesPhysicallyDeletedLikes() {
    User requester = persistUser("requester");
    User writer = persistUser("writer");
    Article article = persistArticle("article");
    Comment comment = persistComment(article, writer, "canceled like comment");
    CommentLike like = persistCommentLike(comment, requester);
    em.flush();

    em.remove(like);
    em.flush();
    em.clear();

    List<RecentCommentLikeActivityProjection> results =
        commentLikeRepository.findRecentCommentLikeActivity(requester.getId());

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("projection 필드를 매핑하고 활성 사용자 좋아요 수만 집계한다")
  void findRecentCommentLikeActivity_mapsProjectionAndCountsActiveLikes() {
    User requester = persistUser("requester");
    User writer = persistUser("writer");
    User activeLiker1 = persistUser("active-liker-1");
    User activeLiker2 = persistUser("active-liker-2");
    User deletedLiker = persistUser("deleted-liker");
    deletedLiker.softDelete();
    Article article = persistArticle("projection article");
    Comment comment = persistComment(article, writer, "projection comment");
    CommentLike requesterLike = persistCommentLike(comment, requester);
    persistCommentLike(comment, activeLiker1);
    persistCommentLike(comment, activeLiker2);
    persistCommentLike(comment, deletedLiker);
    em.flush();

    LocalDateTime commentCreatedAt = LocalDateTime.of(2026, 8, 22, 9, 0);
    LocalDateTime likeCreatedAt = LocalDateTime.of(2026, 8, 22, 10, 0);
    updateCommentCreatedAt(comment, commentCreatedAt);
    updateCommentLikeCreatedAt(requesterLike, likeCreatedAt);
    em.clear();

    List<RecentCommentLikeActivityProjection> results =
        commentLikeRepository.findRecentCommentLikeActivity(requester.getId());

    assertThat(results).hasSize(1);
    RecentCommentLikeActivityProjection result = results.get(0);
    assertThat(result.id()).isEqualTo(requesterLike.getId());
    assertThat(result.createdAt()).isEqualTo(likeCreatedAt);
    assertThat(result.commentId()).isEqualTo(comment.getId());
    assertThat(result.articleId()).isEqualTo(article.getId());
    assertThat(result.articleTitle()).isEqualTo("projection article");
    assertThat(result.commentUserId()).isEqualTo(writer.getId());
    assertThat(result.commentUserNickname()).isEqualTo("writer");
    assertThat(result.commentContent()).isEqualTo("projection comment");
    assertThat(result.commentLikeCount()).isEqualTo(3L);
    assertThat(result.commentCreatedAt()).isEqualTo(commentCreatedAt);
  }

  private User persistUser(String nickname) {
    User user = User.builder()
        .email(UUID.randomUUID() + "@test.com")
        .nickname(nickname)
        .password("password")
        .build();
    em.persist(user);
    return user;
  }

  private Article persistArticle(String title) {
    Article article = Article.create(
        title,
        "summary",
        "https://example.com/articles/" + UUID.randomUUID(),
        LocalDateTime.of(2026, 8, 22, 8, 0),
        ArticleSource.NAVER
    );
    em.persist(article);
    return article;
  }

  private Comment persistComment(Article article, User writer, String content) {
    Comment comment = Comment.builder()
        .article(article)
        .user(writer)
        .content(content)
        .build();
    em.persist(comment);
    return comment;
  }

  private CommentLike persistCommentLike(Comment comment, User likedBy) {
    CommentLike commentLike = CommentLike.builder()
        .comment(comment)
        .likedBy(likedBy)
        .build();
    em.persist(commentLike);
    return commentLike;
  }

  private void updateCommentCreatedAt(Comment comment, LocalDateTime createdAt) {
    em.getEntityManager()
        .createQuery("UPDATE Comment c SET c.createdAt = :createdAt WHERE c.id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", comment.getId())
        .executeUpdate();
  }

  private void updateCommentLikeCreatedAt(CommentLike commentLike, LocalDateTime createdAt) {
    em.getEntityManager()
        .createQuery("UPDATE CommentLike cl SET cl.createdAt = :createdAt WHERE cl.id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", commentLike.getId())
        .executeUpdate();
  }

  private List<UUID> findExpectedLikeIdsByNativeOrder(UUID userId) {
    List<?> rows = em.getEntityManager()
        .createNativeQuery("""
            SELECT id
            FROM comment_likes
            WHERE liked_by = :userId
            ORDER BY created_at DESC, id DESC
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




  @Test
  @DisplayName("활성되어 있는 사용자의 좋아요만 집계하고 likedByMe 여부를 확인한다")
  void countActiveLikesAndExistsActiveLike_excludeSoftDeletedLiker() {
    User writer = persistUser("writer");
    User activeLiker = persistUser("active-liker");
    User deletedLiker = persistUser("deleted-liker");
    Article article = persistArticle("article");
    Comment comment = persistComment(article, writer, "comment");
    persistCommentLike(comment, activeLiker);
    persistCommentLike(comment, deletedLiker);
    deletedLiker.softDelete();
    em.flush();
    em.clear();

    assertThat(commentLikeRepository.countActiveLikesByCommentId(comment.getId())).isEqualTo(1L);
    assertThat(commentLikeRepository.existsActiveByCommentIdAndLikedById(
        comment.getId(), activeLiker.getId())).isTrue();
    assertThat(commentLikeRepository.existsActiveByCommentIdAndLikedById(
        comment.getId(), deletedLiker.getId())).isFalse();
  }



  @Test
  @DisplayName("댓글 물리 삭제 시 해당 ID에 해당하는 모든 좋아요 삭제한다 - RED")
  void deleteByCommentId_deletesOnlyTargetCommentLikes() {
    // given
    User writer = persistUser("writer");
    User liker1 = persistUser("liker1");
    User liker2 = persistUser("liker2");
    Article article = persistArticle("article");

    Comment targetComment = persistComment(article, writer, "target comment");
    Comment otherComment = persistComment(article, writer, "other comment");
    persistCommentLike(targetComment, liker1);
    persistCommentLike(targetComment, liker2);
    persistCommentLike(otherComment, liker1);

    em.flush(); // 테스트 데이터는 실제 데이터베이스에 반영하고 1차 캐시 제거한다
    em.clear();

    // 삭제 실행 전 fixture 확인
    Assertions.assertAll(
        ()->assertThat(commentLikeRepository.countActiveLikesByCommentId(targetComment.getId())).isEqualTo(2L),
        ()->assertThat(commentLikeRepository.countActiveLikesByCommentId(otherComment.getId())).isEqualTo(1L)
    );

    // when
    Long deletedCount=commentLikeRepository.deleteByCommentId(targetComment.getId());
    em.flush();
    em.clear();

    // then
    Assertions.assertAll(
        ()->assertThat(deletedCount).isEqualTo(2L),
        ()->assertThat(commentLikeRepository.countActiveLikesByCommentId(targetComment.getId())).isEqualTo(0L),
        ()->assertThat(commentLikeRepository.countActiveLikesByCommentId(otherComment.getId())).isEqualTo(1L)
    );
  }
}
