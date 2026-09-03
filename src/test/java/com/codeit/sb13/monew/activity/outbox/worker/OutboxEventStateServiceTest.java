package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import java.time.LocalDateTime;
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

    private OutboxEvent claimedEvent(UUID eventId, UUID claimId, int retryCount) {
        OutboxEvent event = mock(OutboxEvent.class);
        given(event.getId()).willReturn(eventId);
        given(event.getClaimId()).willReturn(claimId);
        given(event.getRetryCount()).willReturn(retryCount);
        return event;
    }
}
