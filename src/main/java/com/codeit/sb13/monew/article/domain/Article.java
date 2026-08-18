package com.codeit.sb13.monew.article.domain;

import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "articles",
        indexes = {
                @Index(name = "idx_articles_source", columnList = "source"),
                @Index(name = "idx_articles_date", columnList = "date DESC"),
                @Index(name = "idx_articles_source_date", columnList = "source, date DESC")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Article extends DeletedAtEntity {

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 1000, unique = true)
    private String link;

    @Column(nullable = false)
    private java.time.LocalDateTime date;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount = 0;
}