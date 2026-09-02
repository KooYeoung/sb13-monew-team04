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

  // 댓글 노출 여부는 visibilityStatus를 단일 기준으로 사용한다.
  // 작성자와 기사는 댓글 응답 구성에 필요한 연관 데이터를 가져오기 위해 fetch join한다.
  @Query("""
      SELECT C
      FROM Comment C
      JOIN FETCH C.user U
      JOIN FETCH C.article A
      WHERE C.id = :commentId
          AND C.visibilityStatus = com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE
      """)
  Optional<Comment> findActiveById(@Param("commentId") UUID commentId);

  @Query("""
      SELECT CASE WHEN COUNT(C) > 0 THEN TRUE ELSE FALSE END
      FROM Comment C
      WHERE C.id = :commentId
          AND C.visibilityStatus = com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE
      """)
  boolean existsActiveById(@Param("commentId") UUID commentId);

  // deletedAt이 null인 댓글만 논리 삭제해 이미 삭제된 댓글의 재시도는 0건으로 처리한다.
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

  // 물리 삭제 대상은 deletedAt과 visibilityStatus에 관계 없이 조회한다.
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
