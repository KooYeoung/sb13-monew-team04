package com.codeit.sb13.monew.comment.domain;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "comments",
    indexes = {
        @Index(name = "idx_comments_user_created_id", columnList = "user_id, created_at DESC, id DESC"),
        @Index(name = "idx_comments_article", columnList = "article_id")
    }
)
public class Comment extends DeletedAtEntity { // DeletedAtEntity를 상속받아 공통 필드 사용하도록 수정

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 500)
  private String content;

  @Builder
  public Comment(Article article, User user, String content) {
    this.article = article;
    this.user = user;
    this.content = content;
  }
}
