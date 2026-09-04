package com.codeit.sb13.monew.activity.mongo.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.OutboxProjectionHandler;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReadModelBackfillWorkerTest {

    private final ReadModelBackfillClaimService claimService =
            mock(ReadModelBackfillClaimService.class);
    private final ReadModelBackfillHeartbeat heartbeat = mock(ReadModelBackfillHeartbeat.class);
    private final ReadModelBackfillLease lease = mock(ReadModelBackfillLease.class);
    private final OutboxProjectionHandler projectionHandler =
            mock(OutboxProjectionHandler.class);
    private final ReadModelBackfillVerifier verifier = mock(ReadModelBackfillVerifier.class);
    private final UUID runId = UUID.randomUUID();
    private final UUID claimId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-04T01:00:00Z"), ZoneOffset.UTC);

    private ReadModelBackfillWorker worker;

    @BeforeEach
    void setUp() {
        ReadModelBackfillProperties properties = new ReadModelBackfillProperties(
                true,
                runId,
                1000,
                100,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1)
        );
        worker = new ReadModelBackfillWorker(
                claimService,
                heartbeat,
                projectionHandler,
                verifier,
                properties,
                new ObjectMapper(),
                clock
        );
        given(heartbeat.start(runId, claimId)).willReturn(lease);
    }

    @Test
    @DisplayName("page 전체 MongoDB 반영에 성공한 뒤에만 checkpoint cursor를 완료 처리한다")
    void advancesCheckpointAfterWholePageSucceeds() {
        InitialProjectionEvent first = articleViewEvent();
        InitialProjectionEvent second = articleViewEvent();
        ProjectionSourceBatch source = emptySource();
        given(claimService.claim(runId, 100, Duration.ofMinutes(5)))
                .willReturn(projectWork(List.of(first, second), source));

        ReadModelBackfillResult result = worker.runOnce();

        verify(projectionHandler).project(first, source, LocalDateTime.now(clock));
        verify(projectionHandler).project(second, source, LocalDateTime.now(clock));
        verify(claimService).markProcessed(runId, claimId, 2);
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.failed()).isFalse();
    }

    @Test
    @DisplayName("page 중간 실패 시 cursor를 완료하지 않고 실패 상태를 기록해 전체 범위를 재실행한다")
    void failedPageDoesNotAdvanceCheckpoint() {
        InitialProjectionEvent first = articleViewEvent();
        InitialProjectionEvent second = articleViewEvent();
        ProjectionSourceBatch source = emptySource();
        IllegalStateException failure = new IllegalStateException("mongo failed");
        given(claimService.claim(runId, 100, Duration.ofMinutes(5)))
                .willReturn(projectWork(List.of(first, second), source));
        doThrow(failure).when(projectionHandler)
                .project(second, source, LocalDateTime.now(clock));

        ReadModelBackfillResult result = worker.runOnce();

        verify(claimService, never()).markProcessed(runId, claimId, 2);
        verify(claimService).markFailed(runId, claimId, failure);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isTrue();
    }

    @Test
    @DisplayName("정합성 결과를 JSON으로 저장하고 일치 여부로 run을 완료한다")
    void storesVerificationReport() {
        ReadModelBackfillVerificationReport report = matchedReport();
        given(claimService.claim(runId, 100, Duration.ofMinutes(5)))
                .willReturn(ReadModelBackfillWork.verification(runId, claimId));
        given(verifier.verify(eq(100), any(Runnable.class))).willReturn(report);

        ReadModelBackfillResult result = worker.runOnce();

        org.mockito.ArgumentCaptor<String> reportJson =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(claimService).recordVerification(
                org.mockito.ArgumentMatchers.eq(runId),
                org.mockito.ArgumentMatchers.eq(claimId),
                reportJson.capture(),
                org.mockito.ArgumentMatchers.eq(true)
        );
        verify(verifier).verify(eq(100), any(Runnable.class));
        assertThat(reportJson.getValue()).contains("SUBSCRIPTION", "expectedActivities");
        assertThat(result.verificationMatched()).isTrue();
    }

    private ReadModelBackfillWork projectWork(
            List<InitialProjectionEvent> events,
            ProjectionSourceBatch source
    ) {
        return new ReadModelBackfillWork(
                ReadModelBackfillWorkType.PROJECT,
                runId,
                claimId,
                ReadModelBackfillStage.ARTICLE_VIEWED,
                events,
                source
        );
    }

    private InitialProjectionEvent articleViewEvent() {
        return new InitialProjectionEvent(
                UUID.randomUUID(),
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ArticleOutboxPayload(OutboxEventAction.VIEWED),
                10L,
                LocalDateTime.now(clock)
        );
    }

    private ReadModelBackfillVerificationReport matchedReport() {
        Map<ReadModelBackfillStage,
                ReadModelBackfillVerificationReport.StageVerification> stages =
                new EnumMap<>(ReadModelBackfillStage.class);
        for (ReadModelBackfillStage stage : ReadModelBackfillStage.values()) {
            stages.put(stage, new ReadModelBackfillVerificationReport.StageVerification(
                    0, 0, 0, 0, 0));
        }
        return new ReadModelBackfillVerificationReport(stages);
    }

    private ProjectionSourceBatch emptySource() {
        return new ProjectionSourceBatch(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
