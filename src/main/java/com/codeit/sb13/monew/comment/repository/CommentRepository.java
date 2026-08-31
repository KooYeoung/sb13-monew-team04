package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID>, CommentRepositoryCustom {

  @Query("""
      SELECT C
      FROM Comment C
      JOIN FETCH C.user U
      JOIN FETCH C.article A
      WHERE C.id = :commentId
          AND C.deletedAt IS NULL
          AND U.deletedAt IS NULL
          AND A.deletedAt IS NULL
      """) // 댓글, 작성자, 기사 모두 활성 상태인 경우에만 조회
  Optional<Comment> findActiveById(@Param("commentId") UUID commentId);

  // 논리 삭제는 이미 삭제된 댓글을 다시 성공 처리하지 않도록 DB에서 조건부로 수행한다
  // 댓글 자신의 논리 삭제는 조회 시 노출 상태 판단 우선순위가 가장 높으므로
  // 기존에 ARTICLE_DELETED/USER_DELETED로 표시되어 있었더라도 COMMENT_DELETED로 갱신한다
  @Modifying(clearAutomatically = true)
  @Query("""
      UPDATE Comment C
      SET C.deletedAt = :deletedAt,
          C.updatedAt = :deletedAt,
          C.visibilityStatus = com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.COMMENT_DELETED
      WHERE C.id = :commentId
        AND C.deletedAt IS NULL
      """)
  int softDeleteIfNotDeleted(
      @Param("commentId") UUID commentId,
      @Param("deletedAt") LocalDateTime deletedAt
  );

  // 댓글은 논리 삭제 여부와 관계 없이 물리 삭제 정리 대상에 포함해 조회한다
  @Query("""
     SELECT C
     FROM Comment C
     WHERE C.id = :commentId
  """)
  Optional<Comment> findForHardDeleteById(@Param("commentId") UUID commentId);

  @Query("""
    SELECT new com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection(
        C.id,
        A.id,
        A.title,
        U.id,
        U.nickname,
        C.content,
        (SELECT COUNT(CL)
         FROM CommentLike CL
         WHERE CL.comment.id = C.id
               AND CL.visibilityStatus = 'ACTIVE'),
        C.createdAt
    )
    FROM Comment C
        JOIN C.user U
        JOIN C.article A
    WHERE
        C.user.id = :userId
        AND C.visibilityStatus = 'ACTIVE'
    ORDER BY C.createdAt DESC, C.id DESC
    LIMIT 10
    """)
  List<RecentCommentActivityProjection> findRecentCommentActivities(@Param("userId") UUID userId);

  void deleteByUser_Id(UUID userId);

  // 기사 물리 삭제 시 댓글 정리 (MID4-146)
  void deleteByArticle_Id(UUID articleId);

  // 기사 댓글 수 집계. 활성 댓글만 포함한다. (MID4-163, MID4-225)
  @Query("""
      SELECT COUNT(C)
      FROM Comment C
      WHERE C.article.id = :articleId
        AND C.visibilityStatus = 'ACTIVE'
      """)
  long countActiveByArticleId(@Param("articleId") UUID articleId);
}
