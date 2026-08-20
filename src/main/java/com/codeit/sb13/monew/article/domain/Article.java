package com.codeit.sb13.monew.article.domain;

import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "articles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_articles_link", columnNames = "link")
        },
        indexes = {
                @Index(name = "idx_articles_source_date", columnList = "source, date DESC"),
                @Index(name = "idx_articles_date", columnList = "date DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends DeletedAtEntity {

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 1000)
    private String link;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(nullable = false, length = 50)
    private String source;

    private Article(String title, String summary, String link, LocalDateTime date, String source) {
        this.title = title;
        this.summary = summary;
        this.link = link;
        this.date = date;
        this.source = source;
    }

    /**
     * 기사 생성 팩토리 메서드
     *
     * @param title   기사 제목
     * @param summary 기사 요약
     * @param link    기사 링크
     * @param date    기사 날짜
     * @param source  기사 출처
     * @return 생성된 기사
     */
    public static Article create(String title, String summary, String link,
                                 LocalDateTime date, String source) {
        return new Article(title, summary, link, date, source);
    }

}