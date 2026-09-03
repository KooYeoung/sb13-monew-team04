package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimBatch;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimHeartbeat;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimLease;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimService;
import com.codeit.sb13.monew.activity.outbox.worker.config.OutboxWorkerProperties;
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

class OutboxWorkerCountGroupingTest {

    private final OutboxClaimService claimService = mock(OutboxClaimService.class);
    private final OutboxClaimHeartbeat claimHeartbeat = mock(OutboxClaimHeartbeat.class);
    private final OutboxClaimLease claimLease = mock(OutboxClaimLease.class);
    private final OutboxEventDecoder decoder = mock(OutboxEventDecoder.class);
    private final OutboxProjectionSourceReader sourceReader = mock(OutboxProjectionSourceReader.class);
    private final OutboxProjectionHandler projectionHandler = mock(OutboxProjectionHandler.class);
    private final OutboxEventStateService stateService = mock(OutboxEventStateService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-03T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final UUID claimId = UUID.randomUUID();

    private OutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxWorker(
                claimService,
                claimHeartbeat,
                decoder,
                sourceReader,
                projectionHandler,
                stateService,
                new OutboxWorkerProperties(
                        true,
                        1000,
                        100,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1)
                ),
                clock
        );
        given(claimHeartbeat.start(claimId)).willReturn(claimLease);
    }

    @ParameterizedTest
    @EnumSource(value = OutboxEventType.class, names = {
            "INTEREST_SUBSCRIBER_COUNT_CHANGED",
            "COMMENT_LIKE_CHANGED",
            "ARTICLE_VIEW_COUNT_CHANGED",
            "ARTICLE_COMMENT_COUNT_CHANGED"
    })
    @DisplayName("같은 type과 대상의 count 이벤트는 가장 높은 version으로 한 번 반영한다")
    void mergesCountEventsUsingHighestProjectionVersion(OutboxEventType eventType) {
        UUID targetId = UUID.randomUUID();
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent older = countEvent(eventType, targetId, 3L, 0);
        DecodedOutboxEvent newer = countEvent(eventType, targetId, 7L, 0);
        ProjectionSourceBatch source = emptySource();
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(older);
        given(decoder.decode(second)).willReturn(newer);
        given(sourceReader.read(List.of(newer))).willReturn(source);

        OutboxWorkerResult result = worker.runOnce();

        verify(sourceReader).read(List.of(newer));
        verify(projectionHandler).project(newer, source, fixedNow());
        verify(projectionHandler, never()).project(older, source, fixedNow());
        verify(stateService).markProcessed(
                List.of(older.id(), newer.id()),
                claimId,
                fixedNow()
        );
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 2, 0));
    }

    @Test
    @DisplayName("대상이 같아도 count event type이 다르면 별도 그룹으로 처리한다")
    void keepsDifferentCountEventTypesSeparate() {
        UUID targetId = UUID.randomUUID();
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent viewCount = countEvent(
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED, targetId, 3L, 0);
        DecodedOutboxEvent commentCount = countEvent(
                OutboxEventType.ARTICLE_COMMENT_COUNT_CHANGED, targetId, 4L, 0);
        ProjectionSourceBatch source = emptySource();
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(viewCount);
        given(decoder.decode(second)).willReturn(commentCount);
        given(sourceReader.read(List.of(viewCount, commentCount))).willReturn(source);

        OutboxWorkerResult result = worker.runOnce();

        InOrder order = inOrder(projectionHandler, stateService);
        order.verify(projectionHandler).project(viewCount, source, fixedNow());
        order.verify(stateService).markProcessed(viewCount.id(), claimId, fixedNow());
        order.verify(projectionHandler).project(commentCount, source, fixedNow());
        order.verify(stateService).markProcessed(commentCount.id(), claimId, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 2, 0));
    }

    @Test
    @DisplayName("한 count 그룹이 실패하면 모든 행을 실패 처리하고 다음 그룹을 계속 처리한다")
    void failsWholeCountGroupAndContinuesWithNextGroup() {
        UUID firstTargetId = UUID.randomUUID();
        UUID secondTargetId = UUID.randomUUID();
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        OutboxEvent third = mock(OutboxEvent.class);
        DecodedOutboxEvent retrying = countEvent(
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED, firstTargetId, 3L, 1);
        DecodedOutboxEvent exhausted = countEvent(
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED, firstTargetId, 7L, 4);
        DecodedOutboxEvent nextGroup = countEvent(
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED, secondTargetId, 8L, 0);
        ProjectionSourceBatch source = emptySource();
        IllegalStateException failure = new IllegalStateException("mongo failed");
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second, third)));
        given(decoder.decode(first)).willReturn(retrying);
        given(decoder.decode(second)).willReturn(exhausted);
        given(decoder.decode(third)).willReturn(nextGroup);
        given(sourceReader.read(List.of(exhausted, nextGroup))).willReturn(source);
        org.mockito.Mockito.doThrow(failure)
                .when(projectionHandler).project(exhausted, source, fixedNow());

        OutboxWorkerResult result = worker.runOnce();

        verify(stateService).markFailed(
                List.of(retrying, exhausted),
                claimId,
                failure,
                fixedNow()
        );
        verify(projectionHandler).project(nextGroup, source, fixedNow());
        verify(stateService).markProcessed(nextGroup.id(), claimId, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(3, 1, 2));
    }

    private DecodedOutboxEvent countEvent(
            OutboxEventType eventType,
            UUID aggregateId,
            long projectionVersion,
            int retryCount
    ) {
        return new DecodedOutboxEvent(
                UUID.randomUUID(),
                eventType,
                aggregateType(eventType),
                aggregateId,
                UUID.randomUUID(),
                new CountOutboxPayload(OutboxEventAction.COUNT_CHANGED),
                projectionVersion,
                retryCount,
                fixedNow(),
                fixedNow()
        );
    }

    private OutboxAggregateType aggregateType(OutboxEventType eventType) {
        return switch (eventType) {
            case INTEREST_SUBSCRIBER_COUNT_CHANGED -> OutboxAggregateType.INTEREST;
            case COMMENT_LIKE_CHANGED -> OutboxAggregateType.COMMENT;
            case ARTICLE_VIEW_COUNT_CHANGED, ARTICLE_COMMENT_COUNT_CHANGED ->
                    OutboxAggregateType.ARTICLE;
            default -> throw new AssertionError("count event가 아닙니다: " + eventType);
        };
    }

    private ProjectionSourceBatch emptySource() {
        return new ProjectionSourceBatch(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
        );
    }

    private LocalDateTime fixedNow() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
