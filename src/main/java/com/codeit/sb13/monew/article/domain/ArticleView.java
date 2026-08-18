package com.codeit.sb13.monew.article.domain;

import com.codeit.sb13.monew.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "article_views",
        indexes = {
                @Index(name = "idx_article_views_user_id", columnList = "user_id"),
                @Index(name = "idx_article_views_viewed_at", columnList = "viewed_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_article_views_article_user",
                        columnNames = {"article_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleView extends CreatedAtEntity {

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID articleId;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime viewedAt;

    @PrePersist
    protected void onCreate() {
        if (this.viewedAt == null) {
            this.viewedAt = LocalDateTime.now();
        }
    }
}