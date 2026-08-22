package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.domain.CommentLike;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {

  Long countByCommentId(UUID commentId);

  @Query("""
      select cl
      from CommentLike cl
      join fetch cl.comment c
      join fetch c.article
      join fetch c.user
      join fetch cl.likedBy
      where c.id = :commentId
        and cl.likedBy.id = :likedById
  """)
  Optional<CommentLike> findByCommentAndLikedBy(@Param("commentId") UUID commentId, @Param("likedById") UUID likedById);
}
