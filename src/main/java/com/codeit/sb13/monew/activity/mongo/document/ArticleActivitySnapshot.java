package com.codeit.sb13.monew.activity.mongo.document;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = ARTICLE_SNAPSHOTS)
public record ArticleActivitySnapshot(
        @Id String id,
        String articleId,
        String title,
        String summary,
        ArticleSource source,
        String sourceUrl,
        LocalDateTime publishedAt,
        long viewCount,
        long commentCount,
        boolean visible,
        LocalDateTime updatedAt,
        long projectionVersion,
        boolean tombstone
) {
}
