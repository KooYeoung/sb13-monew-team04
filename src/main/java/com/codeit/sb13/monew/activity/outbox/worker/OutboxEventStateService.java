package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MongoDB 반영 결과를 claim 소유권 조건과 함께 Outbox row에 기록한다.
 *
 * <p>각 상태 갱신은 worker의 긴 처리 흐름과 분리된 {@link Propagation#REQUIRES_NEW}
 * 트랜잭션으로 수행한다. 이벤트 ID와 claim ID가 일치하지 않으면 만료된 실행이
 * 새 소유자의 상태를 덮어쓰지 못하도록 예외를 발생시킨다.</p>
 */
@Service
@RequiredArgsConstructor
public class OutboxEventStateService {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRetryPolicy retryPolicy;

    /**
     * 현재 claim 소유자일 때만 이벤트를 처리 완료 상태로 전환한다.
     *
     * @param eventId 완료할 이벤트 식별자
     * @param claimId 현재 실행의 claim UUID
     * @param processedAt MongoDB 반영 완료 시각
     * @throws OutboxClaimOwnershipLostException claim이 일치하지 않거나 이미 종결된 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId, UUID claimId, LocalDateTime processedAt) {
        int updated = outboxEventRepository.markProcessedIfClaimed(
                eventId,
                claimId,
                processedAt
        );
        requireOwnership(updated, eventId, claimId);
    }

    /**
     * 현재 claim 소유자일 때만 실패 횟수와 다음 상태를 기록한다.
     *
     * <p>최대 실패 횟수 전에는 {@code FAILED}와 다음 재시도 시각을, 최대 횟수에
     * 도달하면 {@code DEAD_LETTER}를 기록한다. 두 경우 모두 현재 claim을 해제한다.</p>
     *
     * @param eventId 실패한 이벤트 식별자
     * @param claimId 현재 실행의 claim UUID
     * @param failure 처리 실패 원인
     * @param failedAt 실패가 발생한 시각
     * @throws OutboxClaimOwnershipLostException claim이 일치하지 않거나 이미 종결된 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            UUID eventId,
            UUID claimId,
            RuntimeException failure,
            LocalDateTime failedAt
    ) {
        OutboxEvent event = outboxEventRepository.findByIdAndClaimId(eventId, claimId)
                .orElseThrow(() -> new OutboxClaimOwnershipLostException(eventId, claimId));
        String error = errorMessage(failure);
        OutboxEventStatus targetStatus;
        LocalDateTime nextRetryAt;
        if (retryPolicy.exhaustedAfter(event.getRetryCount())) {
            targetStatus = OutboxEventStatus.DEAD_LETTER;
            nextRetryAt = null;
        } else {
            targetStatus = OutboxEventStatus.FAILED;
            nextRetryAt = failedAt.plus(retryPolicy.delayAfter(event.getRetryCount()));
        }

        int updated = outboxEventRepository.markFailedIfClaimed(
                eventId,
                claimId,
                event.getRetryCount(),
                targetStatus,
                nextRetryAt,
                error,
                failedAt
        );
        requireOwnership(updated, eventId, claimId);
    }

    private void requireOwnership(int updated, UUID eventId, UUID claimId) {
        if (updated != 1) {
            throw new OutboxClaimOwnershipLostException(eventId, claimId);
        }
    }

    private String errorMessage(RuntimeException failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
