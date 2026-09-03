package com.codeit.sb13.monew.activity.mongo.service;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoReadModelWriterTest {

    @Test
    @DisplayName("activity는 UUID 문자열 natural key와 occurredAt $max로 atomic upsert한다")
    void upsertActivityUsesNaturalKeyAndMonotonicOccurredAt() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoReadModelWriter writer = new MongoReadModelWriter(mongoTemplate);
        UUID sourceActivityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 3, 9, 0);
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        ActivityProjection projection = new ActivityProjection(
                sourceActivityId,
                userId,
                ActivityHistoryType.COMMENT_LIKED,
                ActivityTargetType.COMMENT,
                commentId,
                ActivityTargetType.ARTICLE,
                articleId,
                occurredAt
        );

        writer.upsertActivity(projection, now);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(ActivityHistoryDocument.class),
                eq(ACTIVITY_HISTORIES)
        );
        Document query = queryCaptor.getValue().getQueryObject();
        assertThat(query.get("userId")).isEqualTo(userId.toString());
        assertThat(query.get("type")).isEqualTo(ActivityHistoryType.COMMENT_LIKED);
        assertThat(query.get("targetType")).isEqualTo(ActivityTargetType.COMMENT);
        assertThat(query.get("targetId")).isEqualTo(commentId.toString());

        Document update = updateCaptor.getValue().getUpdateObject();
        assertThat(((Document) update.get("$max")).get("occurredAt")).isEqualTo(occurredAt);
        Document set = (Document) update.get("$set");
        assertThat(set.get("sourceActivityId")).isEqualTo(sourceActivityId.toString());
        assertThat(set.get("visible")).isEqualTo(true);
        assertThat(set.get("status")).isEqualTo(ActivityHistoryStatus.ACTIVE);
        assertThat(set.get("parentTargetId")).isEqualTo(articleId.toString());
    }

    @Test
    @DisplayName("activity 숨김은 존재하는 visible 문서만 갱신하고 새 문서를 만들지 않는다")
    void hideActivityOnlyUpdatesExistingVisibleDocument() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoReadModelWriter writer = new MongoReadModelWriter(mongoTemplate);
        UUID userId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        ActivityProjection projection = new ActivityProjection(
                null,
                userId,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                interestId,
                null,
                null,
                now.minusDays(1)
        );

        writer.hideActivity(
                projection,
                ActivityHistoryStatus.UNSUBSCRIBED,
                null,
                null,
                now
        );

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(ActivityHistoryDocument.class),
                eq(ACTIVITY_HISTORIES)
        );
        assertThat(queryCaptor.getValue().getQueryObject().get("visible")).isEqualTo(true);
        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(set.get("visible")).isEqualTo(false);
        assertThat(set.get("status")).isEqualTo(ActivityHistoryStatus.UNSUBSCRIBED);
    }
}
