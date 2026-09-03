package com.codeit.sb13.monew.activity.mongo.document;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = INTEREST_SNAPSHOTS)
public record InterestActivitySnapshot(
        @Id String id,
        String interestId,
        String name,
        List<String> keywords,
        long subscriberCount,
        boolean visible,
        LocalDateTime updatedAt
) {
}
