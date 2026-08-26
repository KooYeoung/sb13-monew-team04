package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
                 AND CL.likedBy.deletedAt IS NULL),
          C.createdAt
      )
      FROM Comment C
          JOIN C.user U
          JOIN C.article A
      WHERE
          U.id = :userId
          AND U.deletedAt IS NULL
          AND C.deletedAt IS NULL
          AND A.deletedAt IS NULL
      ORDER BY C.createdAt DESC, C.id DESC
      LIMIT 10
      """)
  List<RecentCommentActivityProjection> findRecentCommentActivities(@Param("userId") UUID userId);

  void deleteByUser_Id(UUID userId);

  // 기사 물리 삭제 시 댓글 정리 (MID4-146)
  void deleteByArticle_Id(UUID articleId);

  // 기사 댓글 수 집계. 논리 삭제된 댓글과 탈퇴 사용자의 댓글을 제외한다. (MID4-163)
  long countByArticle_IdAndDeletedAtIsNullAndUser_DeletedAtIsNull(UUID articleId);
}
