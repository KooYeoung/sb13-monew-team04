package com.codeit.sb13.monew.activity.mongo.backfill;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.sb13.monew.activity.mongo.backfill.ReadModelBackfillVerificationReport.StageVerification;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryDocument;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryStatus;
import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import com.codeit.sb13.monew.activity.mongo.document.InterestActivitySnapshot;
import com.codeit.sb13.monew.activity.mongo.service.MongoProjectionKeyFactory;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillProgressException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class ReadModelBackfillVerifierTest {

    private final ReadModelBackfillScanner scanner = mock(ReadModelBackfillScanner.class);
    private final OutboxProjectionSourceReader sourceReader =
            mock(OutboxProjectionSourceReader.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReadModelBackfillVerifier verifier =
            new ReadModelBackfillVerifier(scanner, sourceReader, mongoTemplate);

    @Test
    @DisplayName("노출 활동의 결정적 ID와 snapshot 현재 count가 모두 맞으면 검증에 성공한다")
    void verifiesActivityAndSnapshotCount() {
        UUID relationId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 4, 10, 0);
        InitialProjectionEvent event = new InitialProjectionEvent(
                relationId,
                OutboxEventType.INTEREST_SUBSCRIBED,
                OutboxAggregateType.INTEREST,
                interestId,
                userId,
                new InterestOutboxPayload(OutboxEventAction.SUBSCRIBED),
                0,
                occurredAt
        );
        InitialProjectionPage page = new InitialProjectionPage(List.of(event), relationId);
        given(scanner.scan(ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 0))
                .willReturn(page);
        given(scanner.scan(ReadModelBackfillStage.SUBSCRIPTION, relationId, null, 100, 0))
                .willReturn(emptyPage());
        for (ReadModelBackfillStage stage : List.of(
                ReadModelBackfillStage.COMMENT_WRITTEN,
                ReadModelBackfillStage.COMMENT_LIKED,
                ReadModelBackfillStage.ARTICLE_VIEWED)) {
            given(scanner.scan(stage, null, null, 100, 0)).willReturn(emptyPage());
        }
        ProjectionSourceBatch source = source(userId, interestId, relationId, occurredAt);
        given(sourceReader.read(page.events())).willReturn(source);

        String activityId = MongoProjectionKeyFactory.activity(
                userId,
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                interestId
        );
        ActivityHistoryDocument activity = new ActivityHistoryDocument(
                activityId,
                relationId.toString(),
                userId.toString(),
                ActivityHistoryType.INTEREST_SUBSCRIBED,
                ActivityTargetType.INTEREST,
                interestId.toString(),
                null,
                null,
                occurredAt,
                true,
                ActivityHistoryStatus.ACTIVE,
                null,
                null,
                occurredAt,
                occurredAt,
                5,
                false
        );
        InterestActivitySnapshot snapshot = new InterestActivitySnapshot(
                MongoProjectionKeyFactory.interest(interestId),
                interestId.toString(),
                "관심사",
                List.of("키워드"),
                7,
                true,
                occurredAt,
                5,
                false
        );
        given(mongoTemplate.find(any(Query.class), eq(ActivityHistoryDocument.class),
                eq(ACTIVITY_HISTORIES))).willReturn(List.of(activity));
        given(mongoTemplate.find(any(Query.class), eq(InterestActivitySnapshot.class),
                eq(INTEREST_SNAPSHOTS))).willReturn(List.of(snapshot));
        given(mongoTemplate.count(any(Query.class), eq(ActivityHistoryDocument.class),
                eq(ACTIVITY_HISTORIES))).willReturn(1L, 0L, 0L, 0L);

        ReadModelBackfillVerificationReport report = verifier.verify(100, () -> { });

        StageVerification subscription = report.stages().get(ReadModelBackfillStage.SUBSCRIPTION);
        assertThat(report.matched()).isTrue();
        assertThat(subscription.expectedActivities()).isEqualTo(1);
        assertThat(subscription.actualVisibleActivities()).isEqualTo(1);
        assertThat(subscription.snapshotChecks()).isEqualTo(1);
        assertThat(subscription.snapshotMismatches()).isZero();
    }

    @Test
    @DisplayName("같은 page cursor가 반복되면 무한 조회 대신 진행 예외로 중단한다")
    void rejectsRepeatedCursor() {
        InitialProjectionEvent event = event();
        InitialProjectionPage repeatedPage = new InitialProjectionPage(
                List.of(event), event.sourceRowId());
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 0))
                .willReturn(repeatedPage);
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION,
                event.sourceRowId(),
                null,
                100,
                0
        )).willReturn(repeatedPage);
        given(sourceReader.read(repeatedPage.events())).willReturn(emptySource());

        assertThatThrownBy(() -> verifier.verify(100, () -> { }))
                .isInstanceOfSatisfying(
                        ReadModelBackfillProgressException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsEntry("stage", ReadModelBackfillStage.SUBSCRIPTION)
                                .containsEntry("currentCursor", event.sourceRowId())
                                .containsEntry("nextCursor", event.sourceRowId())
                                .containsEntry("reason", "CURSOR_NOT_CHANGED")
                );

        verify(sourceReader, times(1)).read(repeatedPage.events());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    @DisplayName("이전에 사용한 cursor가 다시 등장하면 순환 진행 예외로 중단한다")
    void rejectsCursorCycle() {
        InitialProjectionEvent first = event();
        InitialProjectionEvent second = event();
        InitialProjectionPage firstPage = new InitialProjectionPage(
                List.of(first), first.sourceRowId());
        InitialProjectionPage secondPage = new InitialProjectionPage(
                List.of(second), second.sourceRowId());
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 0))
                .willReturn(firstPage);
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION,
                first.sourceRowId(),
                null,
                100,
                0
        )).willReturn(secondPage);
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION,
                second.sourceRowId(),
                null,
                100,
                0
        )).willReturn(firstPage);
        given(sourceReader.read(any())).willReturn(emptySource());

        assertThatThrownBy(() -> verifier.verify(100, () -> { }))
                .isInstanceOfSatisfying(
                        ReadModelBackfillProgressException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsEntry("currentCursor", second.sourceRowId())
                                .containsEntry("nextCursor", first.sourceRowId())
                                .containsEntry("reason", "CURSOR_REPEATED")
                );

        verify(sourceReader, times(2)).read(any());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    @DisplayName("page 종료 cursor와 마지막 이벤트 ID가 다르면 검증을 시작하지 않는다")
    void rejectsMismatchedLastEventCursor() {
        InitialProjectionEvent event = event();
        UUID inconsistentCursor = UUID.randomUUID();
        InitialProjectionPage inconsistentPage = new InitialProjectionPage(
                List.of(event), inconsistentCursor);
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 0))
                .willReturn(inconsistentPage);

        assertThatThrownBy(() -> verifier.verify(100, () -> { }))
                .isInstanceOfSatisfying(
                        ReadModelBackfillProgressException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsEntry("reason", "LAST_EVENT_CURSOR_MISMATCH")
                                .containsEntry("nextCursor", inconsistentCursor)
                );

        verifyNoInteractions(sourceReader, mongoTemplate);
    }

    @Test
    @DisplayName("source 조회 뒤 claim lease가 비정상이면 MongoDB 검증을 시작하지 않는다")
    void stopsBeforeMongoReadWhenClaimIsUnhealthy() {
        InitialProjectionEvent event = event();
        InitialProjectionPage page = new InitialProjectionPage(
                List.of(event), event.sourceRowId());
        given(scanner.scan(
                ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 0))
                .willReturn(page);
        given(sourceReader.read(page.events())).willReturn(emptySource());
        AtomicInteger healthChecks = new AtomicInteger();
        Runnable healthCheck = () -> {
            if (healthChecks.incrementAndGet() == 2) {
                throw new IllegalStateException("lease lost");
            }
        };

        assertThatThrownBy(() -> verifier.verify(100, healthCheck))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lease lost");

        verify(scanner).scan(ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 0);
        verify(sourceReader).read(page.events());
        verifyNoInteractions(mongoTemplate);
    }

    private InitialProjectionPage emptyPage() {
        return new InitialProjectionPage(List.of(), null);
    }

    private InitialProjectionEvent event() {
        return new InitialProjectionEvent(
                UUID.randomUUID(),
                OutboxEventType.INTEREST_SUBSCRIBED,
                OutboxAggregateType.INTEREST,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new InterestOutboxPayload(OutboxEventAction.SUBSCRIBED),
                0,
                LocalDateTime.of(2026, 9, 4, 10, 0)
        );
    }

    private ProjectionSourceBatch emptySource() {
        return new ProjectionSourceBatch(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private ProjectionSourceBatch source(
            UUID userId,
            UUID interestId,
            UUID relationId,
            LocalDateTime occurredAt
    ) {
        return new ProjectionSourceBatch(
                Map.of(userId, new UserState(userId, "사용자", true)),
                Map.of(interestId, new InterestState(
                        interestId, "관심사", List.of("키워드"), 7, occurredAt)),
                Map.of(),
                Map.of(),
                Map.of(new RelationKey(interestId, userId), new RelationState(
                        relationId, interestId, userId, true, occurredAt)),
                Map.of(),
                Map.of()
        );
    }
}
