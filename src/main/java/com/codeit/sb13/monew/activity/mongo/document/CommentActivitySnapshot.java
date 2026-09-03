package com.codeit.sb13.monew.activity.mongo.document;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = COMMENT_SNAPSHOTS)
public record CommentActivitySnapshot(
        @Id String id,
        String commentId,
        String articleId,
        String articleTitle,
        String authorUserId,
        String authorNickname,
        String content,
        long likeCount,
        boolean visible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
