package com.codeit.sb13.monew.activity.mongo.service;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.querydsl.MongoQuerydslSupport;
import java.time.LocalDateTime;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoReadModelWriterTest {

    @Test
    @DisplayName("activity는 결정적 _id와 projection version CAS, occurredAt $max로 upsert한다")
    void upsertActivityUsesDeterministicIdVersionCasAndMonotonicOccurredAt() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoReadModelWriter writer = writer(mongoTemplate);
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

        writer.upsertActivity(projection, 7L, now);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(
                any(Query.class),
                updateCaptor.capture(),
                eq(ActivityHistoryDocument.class),
                eq(ACTIVITY_HISTORIES)
        );
        Document update = updateCaptor.getValue().getUpdateObject();
        assertThat(((Document) update.get("$max")).get("occurredAt")).isEqualTo(occurredAt);
        Document set = (Document) update.get("$set");
        assertThat(set.get("sourceActivityId")).isEqualTo(sourceActivityId.toString());
        assertThat(set.get("projectionVersion")).isEqualTo(7L);
        assertThat(set.get("tombstone")).isEqualTo(false);
        assertThat(set.get("visible")).isEqualTo(true);
        assertThat(set.get("status")).isEqualTo(ActivityHistoryStatus.ACTIVE);
        assertThat(set.get("parentTargetId")).isEqualTo(articleId.toString());
    }

    @Test
    @DisplayName("activity 숨김은 문서가 없어도 versioned hidden guard를 upsert한다")
    void hideActivityMaterializesVersionedHiddenGuard() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoReadModelWriter writer = writer(mongoTemplate);
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
                9L,
                now
        );

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(
                any(Query.class),
                updateCaptor.capture(),
                eq(ActivityHistoryDocument.class),
                eq(ACTIVITY_HISTORIES)
        );
        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertThat(set.get("visible")).isEqualTo(false);
        assertThat(set.get("status")).isEqualTo(ActivityHistoryStatus.UNSUBSCRIBED);
        assertThat(set.get("projectionVersion")).isEqualTo(9L);
        assertThat(set.get("tombstone")).isEqualTo(false);
    }

    @Test
    @DisplayName("최신 동일 _id로 확인되지 않은 duplicate key는 숨기지 않고 전파한다")
    void unrelatedDuplicateKeyIsPropagated() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoReadModelWriter writer = writer(mongoTemplate);
        ActivityProjection projection = new ActivityProjection(
                UUID.randomUUID(), UUID.randomUUID(), ActivityHistoryType.ARTICLE_VIEWED,
                ActivityTargetType.ARTICLE, UUID.randomUUID(), null, null,
                LocalDateTime.of(2026, 9, 3, 10, 0));
        DuplicateKeyException duplicate = new DuplicateKeyException("unexpected conflict");
        org.mockito.Mockito.when(mongoTemplate.upsert(
                        any(Query.class), any(Update.class),
                        eq(ActivityHistoryDocument.class), eq(ACTIVITY_HISTORIES)))
                .thenThrow(duplicate);
        org.mockito.Mockito.when(mongoTemplate.exists(
                        any(Query.class), eq(ActivityHistoryDocument.class),
                        eq(ACTIVITY_HISTORIES)))
                .thenReturn(false);

        assertThatThrownBy(() -> writer.upsertActivity(projection, 3L,
                LocalDateTime.of(2026, 9, 3, 10, 0)))
                .isSameAs(duplicate);
    }

    private MongoReadModelWriter writer(MongoTemplate mongoTemplate) {
        MongoQuerydslSupport querydsl = mock(MongoQuerydslSupport.class);
        org.mockito.Mockito.when(querydsl.toQuery(any(), any(), any()))
                .thenReturn(new Query());
        return new MongoReadModelWriter(mongoTemplate, querydsl);
    }
}
