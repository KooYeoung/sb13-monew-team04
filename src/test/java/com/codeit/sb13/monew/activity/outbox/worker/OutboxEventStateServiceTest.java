package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventStateServiceTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private OutboxEventStateService service;

    @BeforeEach
    void setUp() {
        service = new OutboxEventStateService(repository, new OutboxRetryPolicy());
    }

    @Test
    @DisplayName("첫 처리 실패는 claim 소유권을 확인하고 1분 뒤 FAILED 재시도로 기록한다")
    void firstFailureSchedulesRetry() {
        UUID eventId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        OutboxEvent event = claimedEvent(eventId, claimId, 0);
        given(repository.findByIdAndClaimId(eventId, claimId)).willReturn(Optional.of(event));
        given(repository.markFailedIfClaimed(
                eventId,
                claimId,
                0,
                OutboxEventStatus.FAILED,
                failedAt.plusMinutes(1),
                "IllegalStateException: mongo unavailable",
                failedAt
        )).willReturn(1);

        service.markFailed(
                eventId,
                claimId,
                new IllegalStateException("mongo unavailable"),
                failedAt
        );

        verify(repository).markFailedIfClaimed(
                eventId,
                claimId,
                0,
                OutboxEventStatus.FAILED,
                failedAt.plusMinutes(1),
                "IllegalStateException: mongo unavailable",
                failedAt
        );
    }

    @Test
    @DisplayName("다섯 번째 처리 실패는 claim을 해제하며 DEAD_LETTER로 기록한다")
    void fifthFailureBecomesDeadLetter() {
        UUID eventId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        OutboxEvent event = claimedEvent(eventId, claimId, 4);
        given(repository.findByIdAndClaimId(eventId, claimId)).willReturn(Optional.of(event));
        given(repository.markFailedIfClaimed(
                eventId,
                claimId,
                4,
                OutboxEventStatus.DEAD_LETTER,
                null,
                "IllegalStateException: retry exhausted",
                failedAt
        )).willReturn(1);

        service.markFailed(
                eventId,
                claimId,
                new IllegalStateException("retry exhausted"),
                failedAt
        );

        verify(repository).markFailedIfClaimed(
                eventId,
                claimId,
                4,
                OutboxEventStatus.DEAD_LETTER,
                null,
                "IllegalStateException: retry exhausted",
                failedAt
        );
    }

    @Test
    @DisplayName("완료 갱신 시 claim UUID가 다르면 소유권 상실 예외를 던진다")
    void markProcessedRejectsLostClaim() {
        UUID eventId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        LocalDateTime processedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        given(repository.markProcessedIfClaimed(eventId, claimId, processedAt)).willReturn(0);

        assertThatThrownBy(() -> service.markProcessed(eventId, claimId, processedAt))
                .isInstanceOf(OutboxClaimOwnershipLostException.class);
    }

    @Test
    @DisplayName("count 그룹 완료 처리는 모든 이벤트의 claim 소유권을 확인한다")
    void marksCountGroupProcessed() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        LocalDateTime processedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        List<UUID> eventIds = List.of(firstId, secondId);
        given(repository.markAllProcessedIfClaimed(eventIds, claimId, processedAt))
                .willReturn(2);

        service.markProcessed(eventIds, claimId, processedAt);

        verify(repository).markAllProcessedIfClaimed(eventIds, claimId, processedAt);
    }

    @Test
    @DisplayName("count 그룹 완료 행 수가 다르면 claim 소유권 상실 예외를 던진다")
    void rejectsPartiallyOwnedCountGroup() {
        List<UUID> eventIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        UUID claimId = UUID.randomUUID();
        LocalDateTime processedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        given(repository.markAllProcessedIfClaimed(eventIds, claimId, processedAt))
                .willReturn(1);

        assertThatThrownBy(() -> service.markProcessed(eventIds, claimId, processedAt))
                .isInstanceOf(OutboxClaimOwnershipLostException.class);
    }

    @Test
    @DisplayName("count 그룹 실패는 행별 retry 이력을 유지해 FAILED와 DEAD_LETTER로 나눈다")
    void marksCountGroupFailedByIndividualRetryCount() {
        UUID claimId = UUID.randomUUID();
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        IllegalStateException failure = new IllegalStateException("mongo unavailable");
        DecodedOutboxEvent retrying = countEvent(1);
        DecodedOutboxEvent exhausted = countEvent(4);
        given(repository.markAllFailedIfClaimed(
                List.of(retrying.id()),
                claimId,
                1,
                OutboxEventStatus.FAILED,
                failedAt.plusMinutes(5),
                "IllegalStateException: mongo unavailable",
                failedAt
        )).willReturn(1);
        given(repository.markAllFailedIfClaimed(
                List.of(exhausted.id()),
                claimId,
                4,
                OutboxEventStatus.DEAD_LETTER,
                null,
                "IllegalStateException: mongo unavailable",
                failedAt
        )).willReturn(1);

        service.markFailed(List.of(retrying, exhausted), claimId, failure, failedAt);

        verify(repository).markAllFailedIfClaimed(
                List.of(retrying.id()),
                claimId,
                1,
                OutboxEventStatus.FAILED,
                failedAt.plusMinutes(5),
                "IllegalStateException: mongo unavailable",
                failedAt
        );
        verify(repository).markAllFailedIfClaimed(
                List.of(exhausted.id()),
                claimId,
                4,
                OutboxEventStatus.DEAD_LETTER,
                null,
                "IllegalStateException: mongo unavailable",
                failedAt
        );
    }

    private OutboxEvent claimedEvent(UUID eventId, UUID claimId, int retryCount) {
        OutboxEvent event = mock(OutboxEvent.class);
        given(event.getId()).willReturn(eventId);
        given(event.getClaimId()).willReturn(claimId);
        given(event.getRetryCount()).willReturn(retryCount);
        return event;
    }

    private DecodedOutboxEvent countEvent(int retryCount) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        return new DecodedOutboxEvent(
                UUID.randomUUID(),
                OutboxEventType.ARTICLE_VIEW_COUNT_CHANGED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CountOutboxPayload(OutboxEventAction.COUNT_CHANGED),
                1L,
                retryCount,
                now,
                now
        );
    }
}
