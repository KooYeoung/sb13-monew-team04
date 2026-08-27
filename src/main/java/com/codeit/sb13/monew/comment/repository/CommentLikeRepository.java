package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.domain.CommentLike;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.codeit.sb13.monew.comment.repository.dto.RecentCommentLikeActivityProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {

  @Query("""
  SELECT COUNT(CL)
      FROM CommentLike CL
      JOIN CL.comment C
      JOIN CL.likedBy LB
      JOIN C.article A
      JOIN C.user U
      WHERE C.id = :commentId
          AND LB.deletedAt IS NULL
          AND C.deletedAt IS NULL
          AND A.deletedAt IS NULL
          AND U.deletedAt IS NULL
  """) // 탈퇴 또는 논리 삭제된 유저의 좋아요는 카운트에서 제외
  Long countActiveLikesByCommentId(@Param("commentId") UUID commentId);

  @Query("""
      SELECT CASE WHEN COUNT(CL) > 0 THEN TRUE ELSE FALSE END
      FROM CommentLike CL
      JOIN CL.comment C
      JOIN CL.likedBy LB
      JOIN C.article A
      JOIN C.user U
      WHERE C.id = :commentId
          AND LB.id = :likedById
          AND LB.deletedAt IS NULL
          AND C.deletedAt IS NULL
          AND A.deletedAt IS NULL
          AND U.deletedAt IS NULL
      """) // 조건에 맞는 데이터가 0개 이상이면 true, 없으면 false 반환
  boolean existsActiveByCommentIdAndLikedById(
      @Param("commentId") UUID commentId,
      @Param("likedById") UUID likedById
  );

  // CommentLikeDto 생성에는 좋아요 자체와 댓글·기사·작성자 정보가 모두 필요하다
  @Query("""
      SELECT CL
      FROM CommentLike CL
      JOIN FETCH CL.comment C
      JOIN FETCH C.article A
      JOIN FETCH C.user U
      JOIN FETCH CL.likedBy LB
      WHERE C.id = :commentId
          AND CL.likedBy.id = :likedById
          AND C.deletedAt IS NULL
          AND U.deletedAt IS NULL
          AND A.deletedAt IS NULL
          AND LB.deletedAt IS NULL
      """) // 활성 조건 추가, 논리 삭제된 데이터는 제외
  Optional<CommentLike> findWithCommentDetailsByCommentIdAndLikedById(
      @Param("commentId") UUID commentId,
      @Param("likedById") UUID likedById
  );

  void deleteByComment_User_Id(UUID userId);
  void deleteByLikedBy_Id(UUID userId);

  // 기사 물리 삭제 시 해당 기사 댓글의 좋아요 정리 (MID4-146)
  void deleteByComment_Article_Id(UUID articleId);


  @Query("""
    SELECT new com.codeit.sb13.monew.comment.repository.dto.RecentCommentLikeActivityProjection(
        CL.id,
        CL.createdAt,
        C.id,
        A.id,
        A.title,
        U.id,
        U.nickname,
        C.content,
         (SELECT COUNT(CL2)
          FROM CommentLike CL2
          JOIN CL2.comment C2
          JOIN CL2.likedBy LB2
          WHERE C2.id = C.id
              AND LB2.deletedAt IS NULL),
          C.createdAt
        )
    FROM CommentLike CL
    JOIN CL.comment C
    JOIN CL.likedBy LB
    JOIN C.user U
    JOIN C.article A
    WHERE LB.id = :userId
        AND LB.deletedAt IS NULL
        AND U.deletedAt IS NULL
        AND A.deletedAt IS NULL
        AND C.deletedAt IS NULL
    ORDER BY CL.createdAt DESC, CL.id DESC
    LIMIT 10
    """)
  List<RecentCommentLikeActivityProjection> findRecentCommentLikeActivity(@Param("userId") UUID userId);


  // 쿼리 실행 전 쓰기 지연 저장소 남아 있는 쿼리 미리 flush
  @Modifying(flushAutomatically = true)
  @Query("""
      DELETE FROM CommentLike CL
      WHERE CL.comment.id = :commentId
        AND CL.likedBy.id = :likedById
      """)
  Long deleteByCommentIdAndLikedById(
      @Param("commentId") UUID commentId,
      @Param("likedById") UUID likedById
  );


  // 쿼리 실행 전 지연 저장소 쿼리 flush
  @Modifying(flushAutomatically = true)
  @Query("""
      DELETE FROM CommentLike CL
      WHERE CL.comment.id = :commentId
      """)
  Long deleteByCommentId(@Param("commentId") UUID commentId);

}
