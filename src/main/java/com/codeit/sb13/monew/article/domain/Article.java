package com.codeit.sb13.monew.article.domain;

import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "articles",
        indexes = {
                @Index(name = "idx_articles_source_date", columnList = "source, date DESC"),
                @Index(name = "idx_articles_date", columnList = "date DESC")
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
    private LocalDateTime date;

    @Column(nullable = false, length = 50)
    private String source;

}