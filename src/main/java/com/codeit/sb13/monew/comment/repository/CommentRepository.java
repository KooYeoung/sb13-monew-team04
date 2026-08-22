package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  Optional<Comment> findByIdAndDeletedAtIsNull(UUID commentId);

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
}
