package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
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
import org.mockito.InOrder;

class OutboxWorkerTest {

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

    @Test
    @DisplayName("선택된 이벤트를 조회 순서대로 하나씩 MongoDB에 반영하고 완료 처리한다")
    void processesEventsSequentially() {
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent firstDecoded = decoded(UUID.randomUUID());
        DecodedOutboxEvent secondDecoded = decoded(UUID.randomUUID());
        ProjectionSourceBatch source = emptySource();
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(firstDecoded);
        given(decoder.decode(second)).willReturn(secondDecoded);
        given(sourceReader.read(List.of(firstDecoded, secondDecoded))).willReturn(source);

        OutboxWorkerResult result = worker.runOnce();

        InOrder order = inOrder(projectionHandler, stateService);
        order.verify(projectionHandler).project(firstDecoded, source, fixedNow());
        order.verify(stateService).markProcessed(firstDecoded.id(), claimId, fixedNow());
        order.verify(projectionHandler).project(secondDecoded, source, fixedNow());
        order.verify(stateService).markProcessed(secondDecoded.id(), claimId, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 2, 0));
    }

    @Test
    @DisplayName("한 이벤트가 실패해도 다음 이벤트를 계속 처리한다")
    void continuesAfterIndividualFailure() {
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent firstDecoded = decoded(UUID.randomUUID());
        DecodedOutboxEvent secondDecoded = decoded(UUID.randomUUID());
        ProjectionSourceBatch source = emptySource();
        IllegalStateException failure = new IllegalStateException("mongo failed");
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(firstDecoded);
        given(decoder.decode(second)).willReturn(secondDecoded);
        given(sourceReader.read(List.of(firstDecoded, secondDecoded))).willReturn(source);
        org.mockito.Mockito.doThrow(failure)
                .when(projectionHandler).project(firstDecoded, source, fixedNow());

        OutboxWorkerResult result = worker.runOnce();

        verify(stateService).markFailed(firstDecoded.id(), claimId, failure, fixedNow());
        verify(stateService).markProcessed(secondDecoded.id(), claimId, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 1, 1));
    }

    @Test
    @DisplayName("한 이벤트의 decode가 실패해도 실패 상태를 저장하고 다음 이벤트를 처리한다")
    void continuesAfterDecodeFailure() {
        UUID failedEventId = UUID.randomUUID();
        OutboxEvent failedEvent = mock(OutboxEvent.class);
        OutboxEvent successfulEvent = mock(OutboxEvent.class);
        DecodedOutboxEvent decodedEvent = decoded(UUID.randomUUID());
        ProjectionSourceBatch source = emptySource();
        IllegalArgumentException failure = new IllegalArgumentException("invalid payload");
        given(failedEvent.getId()).willReturn(failedEventId);
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(failedEvent, successfulEvent)));
        given(decoder.decode(failedEvent)).willThrow(failure);
        given(decoder.decode(successfulEvent)).willReturn(decodedEvent);
        given(sourceReader.read(List.of(decodedEvent))).willReturn(source);

        OutboxWorkerResult result = worker.runOnce();

        verify(stateService).markFailed(failedEventId, claimId, failure, fixedNow());
        verify(projectionHandler).project(decodedEvent, source, fixedNow());
        verify(stateService).markProcessed(decodedEvent.id(), claimId, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 1, 1));
    }

    @Test
    @DisplayName("decode 실패 상태를 저장하지 못하면 남은 이벤트 처리를 중단한다")
    void decodeFailureStateSaveFailureStopsBatch() {
        UUID failedEventId = UUID.randomUUID();
        OutboxEvent failedEvent = mock(OutboxEvent.class);
        OutboxEvent remainingEvent = mock(OutboxEvent.class);
        IllegalArgumentException decodeFailure = new IllegalArgumentException("invalid payload");
        IllegalStateException stateFailure = new IllegalStateException("state save failed");
        given(failedEvent.getId()).willReturn(failedEventId);
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(failedEvent, remainingEvent)));
        given(decoder.decode(failedEvent)).willThrow(decodeFailure);
        doThrow(stateFailure)
                .when(stateService)
                .markFailed(failedEventId, claimId, decodeFailure, fixedNow());

        OutboxWorkerResult result = worker.runOnce();

        verify(decoder, never()).decode(remainingEvent);
        verifyNoInteractions(sourceReader, projectionHandler);
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 0, 0));
        assertThat(result.unprocessed()).isEqualTo(2);
    }

    @Test
    @DisplayName("RDB batch 조회가 실패하면 선택된 이벤트를 모두 실패 처리한다")
    void sourceReadFailureFailsAllDecodedEvents() {
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent firstDecoded = decoded(UUID.randomUUID());
        DecodedOutboxEvent secondDecoded = decoded(UUID.randomUUID());
        IllegalStateException failure = new IllegalStateException("rdb failed");
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(firstDecoded);
        given(decoder.decode(second)).willReturn(secondDecoded);
        given(sourceReader.read(List.of(firstDecoded, secondDecoded))).willThrow(failure);

        OutboxWorkerResult result = worker.runOnce();

        verify(stateService).markFailed(firstDecoded.id(), claimId, failure, fixedNow());
        verify(stateService).markFailed(secondDecoded.id(), claimId, failure, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 0, 2));
    }

    @Test
    @DisplayName("RDB batch 조회 실패 상태 저장이 중단되면 저장 완료된 건만 실패로 집계한다")
    void sourceReadFailureCountsOnlyPersistedFailureStates() {
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent firstDecoded = decoded(UUID.randomUUID());
        DecodedOutboxEvent secondDecoded = decoded(UUID.randomUUID());
        IllegalStateException readFailure = new IllegalStateException("rdb failed");
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(firstDecoded);
        given(decoder.decode(second)).willReturn(secondDecoded);
        given(sourceReader.read(List.of(firstDecoded, secondDecoded))).willThrow(readFailure);
        doThrow(new IllegalStateException("state save failed"))
                .when(stateService)
                .markFailed(firstDecoded.id(), claimId, readFailure, fixedNow());

        OutboxWorkerResult result = worker.runOnce();

        verify(stateService, never())
                .markFailed(secondDecoded.id(), claimId, readFailure, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 0, 0));
        assertThat(result.unprocessed()).isEqualTo(2);
    }

    @Test
    @DisplayName("projection 실패 상태를 저장하지 못하면 실패로 집계하지 않고 처리를 중단한다")
    void projectionFailureStateSaveFailureIsUnprocessed() {
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent firstDecoded = decoded(UUID.randomUUID());
        DecodedOutboxEvent secondDecoded = decoded(UUID.randomUUID());
        ProjectionSourceBatch source = emptySource();
        IllegalStateException projectionFailure = new IllegalStateException("mongo failed");
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(firstDecoded);
        given(decoder.decode(second)).willReturn(secondDecoded);
        given(sourceReader.read(List.of(firstDecoded, secondDecoded))).willReturn(source);
        doThrow(projectionFailure)
                .when(projectionHandler).project(firstDecoded, source, fixedNow());
        doThrow(new IllegalStateException("state save failed"))
                .when(stateService)
                .markFailed(firstDecoded.id(), claimId, projectionFailure, fixedNow());

        OutboxWorkerResult result = worker.runOnce();

        verify(projectionHandler, never()).project(secondDecoded, source, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 0, 0));
        assertThat(result.unprocessed()).isEqualTo(2);
    }

    @Test
    @DisplayName("MongoDB 반영 후 lease를 잃으면 완료 상태를 저장하지 않는다")
    void leaseFailureAfterProjectionSkipsCompletion() {
        OutboxEvent event = mock(OutboxEvent.class);
        DecodedOutboxEvent decodedEvent = decoded(UUID.randomUUID());
        ProjectionSourceBatch source = emptySource();
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(event)));
        given(decoder.decode(event)).willReturn(decodedEvent);
        given(sourceReader.read(List.of(decodedEvent))).willReturn(source);
        doNothing()
                .doNothing()
                .doNothing()
                .doThrow(new IllegalStateException("lease lost"))
                .when(claimLease)
                .verifyHealthy();

        OutboxWorkerResult result = worker.runOnce();

        verify(projectionHandler).project(decodedEvent, source, fixedNow());
        verify(stateService, never()).markProcessed(decodedEvent.id(), claimId, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(1, 0, 0));
        assertThat(result.unprocessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("완료 상태를 저장하지 못하면 다음 이벤트를 처리하지 않는다")
    void processedStateSaveFailureStopsRemainingEvents() {
        OutboxEvent first = mock(OutboxEvent.class);
        OutboxEvent second = mock(OutboxEvent.class);
        DecodedOutboxEvent firstDecoded = decoded(UUID.randomUUID());
        DecodedOutboxEvent secondDecoded = decoded(UUID.randomUUID());
        ProjectionSourceBatch source = emptySource();
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(first, second)));
        given(decoder.decode(first)).willReturn(firstDecoded);
        given(decoder.decode(second)).willReturn(secondDecoded);
        given(sourceReader.read(List.of(firstDecoded, secondDecoded))).willReturn(source);
        doThrow(new IllegalStateException("state save failed"))
                .when(stateService)
                .markProcessed(firstDecoded.id(), claimId, fixedNow());

        OutboxWorkerResult result = worker.runOnce();

        verify(projectionHandler).project(firstDecoded, source, fixedNow());
        verify(projectionHandler, never()).project(secondDecoded, source, fixedNow());
        assertThat(result).isEqualTo(new OutboxWorkerResult(2, 0, 0));
        assertThat(result.unprocessed()).isEqualTo(2);
    }

    @Test
    @DisplayName("claim heartbeat가 실패하면 MongoDB 반영을 시작하지 않는다")
    void heartbeatFailureStopsProcessing() {
        OutboxEvent event = mock(OutboxEvent.class);
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(event)));
        doThrow(new IllegalStateException("heartbeat failed"))
                .when(claimLease).verifyHealthy();

        OutboxWorkerResult result = worker.runOnce();

        assertThat(result).isEqualTo(new OutboxWorkerResult(1, 0, 0));
        assertThat(result.unprocessed()).isEqualTo(1);
        verifyNoInteractions(decoder, sourceReader, projectionHandler, stateService);
    }

    @Test
    @DisplayName("heartbeat 시작에 실패하면 처리 전 batch claim을 즉시 해제한다")
    void heartbeatStartFailureReleasesClaim() {
        OutboxEvent event = mock(OutboxEvent.class);
        given(claimService.claim(100, Duration.ofMinutes(5)))
                .willReturn(new OutboxClaimBatch(claimId, List.of(event)));
        given(claimHeartbeat.start(claimId))
                .willThrow(new IllegalStateException("executor unavailable"));

        OutboxWorkerResult result = worker.runOnce();

        assertThat(result).isEqualTo(new OutboxWorkerResult(1, 0, 0));
        verify(claimService).release(claimId);
        verifyNoInteractions(decoder, sourceReader, projectionHandler, stateService);
    }

    private DecodedOutboxEvent decoded(UUID id) {
        return new DecodedOutboxEvent(
                id,
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ArticleOutboxPayload(
                        com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction.VIEWED
                ),
                1L,
                0,
                fixedNow(),
                fixedNow()
        );
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
