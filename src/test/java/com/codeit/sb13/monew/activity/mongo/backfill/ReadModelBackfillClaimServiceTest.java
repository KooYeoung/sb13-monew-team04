package com.codeit.sb13.monew.activity.mongo.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.service.OutboxProjectionVersionAllocator;
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ReadModelBackfillClaimServiceTest {

    private final ReadModelBackfillRunRepository repository =
            mock(ReadModelBackfillRunRepository.class);
    private final ReadModelBackfillRunInitializer initializer =
            mock(ReadModelBackfillRunInitializer.class);
    private final ReadModelBackfillScanner scanner = mock(ReadModelBackfillScanner.class);
    private final OutboxProjectionVersionAllocator versionAllocator =
            mock(OutboxProjectionVersionAllocator.class);
    private final OutboxProjectionSourceReader sourceReader =
            mock(OutboxProjectionSourceReader.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);

    @Test
    @DisplayName("projection version 발급 후 page와 현재 source를 같은 claim transaction에서 고정한다")
    void allocatesVersionBeforeReadingPageAndSource() {
        UUID runId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID interestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReadModelBackfillRun run = ReadModelBackfillRun.start(runId, now);
        InitialProjectionEvent event = new InitialProjectionEvent(
                sourceId,
                OutboxEventType.INTEREST_SUBSCRIBED,
                OutboxAggregateType.INTEREST,
                interestId,
                userId,
                new InterestOutboxPayload(OutboxEventAction.SUBSCRIBED),
                11L,
                now
        );
        InitialProjectionPage page = new InitialProjectionPage(List.of(event), sourceId);
        ProjectionSourceBatch source = emptySource();
        given(repository.findByIdForUpdate(runId)).willReturn(Optional.of(run));
        given(repository.currentTime()).willReturn(now);
        given(versionAllocator.allocate()).willReturn(11L);
        given(scanner.scan(ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 11L))
                .willReturn(page);
        given(sourceReader.read(page.events())).willReturn(source);
        ReadModelBackfillClaimService service = new ReadModelBackfillClaimService(
                repository, initializer, scanner, versionAllocator, sourceReader);

        ReadModelBackfillWork work = service.claim(runId, 100, Duration.ofMinutes(5));

        InOrder order = inOrder(versionAllocator, scanner, sourceReader);
        order.verify(versionAllocator).allocate();
        order.verify(scanner).scan(
                ReadModelBackfillStage.SUBSCRIPTION, null, null, 100, 11L);
        order.verify(sourceReader).read(page.events());
        assertThat(work.type()).isEqualTo(ReadModelBackfillWorkType.PROJECT);
        assertThat(work.events()).containsExactly(event);
        assertThat(work.source()).isSameAs(source);
        assertThat(run.getPendingLastId()).isEqualTo(sourceId);
        assertThat(run.getStatus()).isEqualTo(ReadModelBackfillStatus.RUNNING);
    }

    @Test
    @DisplayName("완료된 run-id는 page를 다시 읽지 않고 no-op한다")
    void completedRunIsNoOp() {
        UUID runId = UUID.randomUUID();
        ReadModelBackfillRun run = completedRun(runId);
        given(repository.findByIdForUpdate(runId)).willReturn(Optional.of(run));
        given(repository.currentTime()).willReturn(now);
        ReadModelBackfillClaimService service = new ReadModelBackfillClaimService(
                repository, initializer, scanner, versionAllocator, sourceReader);

        ReadModelBackfillWork work = service.claim(runId, 100, Duration.ofMinutes(5));

        assertThat(work.isEmpty()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(scanner, versionAllocator, sourceReader);
    }

    private ReadModelBackfillRun completedRun(UUID runId) {
        ReadModelBackfillRun run = ReadModelBackfillRun.start(runId, now);
        for (int i = 0; i < ReadModelBackfillStage.values().length; i++) {
            run.advanceStage(now.plusSeconds(i));
        }
        UUID claimId = UUID.randomUUID();
        run.claimVerification(claimId, now.plusMinutes(1), now.plusMinutes(2));
        run.recordVerification(claimId, "{}", true, now.plusMinutes(1));
        return run;
    }

    private ProjectionSourceBatch emptySource() {
        return new ProjectionSourceBatch(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
