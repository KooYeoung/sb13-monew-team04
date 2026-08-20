package com.codeit.sb13.monew.article.domain;

import com.codeit.sb13.monew.global.domain.CreatedAtEntity;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "article_views",
        indexes = {
                @Index(name = "idx_article_views_article_viewed", columnList = "article_id, viewed_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_article_views_article_user",
                        columnNames = {"article_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleView extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = true)
    private LocalDateTime viewedAt;

    private ArticleView(Article article, User user, LocalDateTime viewedAt) {
        this.article = article;
        this.user = user;
        this.viewedAt = viewedAt;
    }

    // 조회 기록 생성 팩토리 메서드
    public static ArticleView create(Article article, User user, LocalDateTime viewedAt) {
        return new ArticleView(article, user, viewedAt);
    }

    @PrePersist
    protected void onCreate() {
        if (this.viewedAt == null) {
            this.viewedAt = LocalDateTime.now();
        }
    }

    // 조회 시간 업데이트
    public void updateViewedAt(LocalDateTime viewedAt) {
        this.viewedAt = viewedAt;
    }
}
