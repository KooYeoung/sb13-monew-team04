package com.codeit.sb13.monew.comment.domain;

import com.codeit.sb13.monew.global.domain.CreatedAtEntity;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동일한 사용자가 같은 댓글에 중복 좋아요를 생성하지 않도록
 * comment_id와 liked_by 조합에 UNIQUE 제약 조건 적용
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "comment_likes",
    indexes = {
        @Index(
            name = "idx_comment_likes_liked_by_created_id",
            columnList = "liked_by, created_at DESC, id DESC"
        )
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_comment_likes_comment_liked_by",
            columnNames = {"comment_id", "liked_by"}
        )
    }
)
public class CommentLike extends CreatedAtEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "comment_id", nullable = false)
  private Comment comment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "liked_by", nullable = false)
  private User likedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility_status", nullable = false, length = 50)
  private ActivityVisibilityStatus visibilityStatus;

  @Builder
  public CommentLike(Comment comment, User likedBy) {
    this.comment = comment;
    this.likedBy = likedBy;
    this.visibilityStatus = ActivityVisibilityStatus.ACTIVE;
  }
}
