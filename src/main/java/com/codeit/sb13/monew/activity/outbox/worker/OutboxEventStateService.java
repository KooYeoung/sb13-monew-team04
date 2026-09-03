package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import com.codeit.sb13.monew.global.exception.outbox.OutboxClaimOwnershipLostException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
     * 하나의 MongoDB projection으로 처리한 count 이벤트 그룹 전체를 완료 처리한다.
     *
     * <p>한 번의 조건부 bulk update 결과가 요청한 이벤트 수와 다르면 transaction을
     * 롤백해 일부 행만 {@code PROCESSED}로 남지 않게 한다.</p>
     *
     * @param eventIds 같은 count projection을 공유하는 Outbox 이벤트 ID 목록
     * @param claimId 현재 실행의 claim UUID
     * @param processedAt MongoDB 반영 완료 시각
     * @throws OutboxClaimOwnershipLostException 그룹 중 하나라도 claim 소유권을 잃은 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(
            List<UUID> eventIds,
            UUID claimId,
            LocalDateTime processedAt
    ) {
        List<UUID> targets = List.copyOf(eventIds);
        if (targets.isEmpty()) {
            return;
        }
        int updated = outboxEventRepository.markAllProcessedIfClaimed(
                targets,
                claimId,
                processedAt
        );
        requireOwnership(updated, targets, claimId);
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

    /**
     * 하나의 count projection에서 함께 실패한 이벤트들의 retry 상태를 원자적으로 기록한다.
     *
     * <p>같은 오류와 실패 시각을 사용하되 기존 retry 횟수에 따라 이벤트를 나눠
     * {@code FAILED} 또는 {@code DEAD_LETTER}로 전환한다. 어느 갱신에서든 claim
     * 소유권이 일치하지 않으면 이 메서드의 모든 상태 변경을 롤백한다.</p>
     *
     * @param events 같은 count projection을 공유하는 decode 이벤트 목록
     * @param claimId 현재 실행의 claim UUID
     * @param failure 공통 projection 실패 원인
     * @param failedAt 실패 발생 시각
     * @throws OutboxClaimOwnershipLostException 그룹 중 하나라도 claim 소유권을 잃은 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            List<DecodedOutboxEvent> events,
            UUID claimId,
            RuntimeException failure,
            LocalDateTime failedAt
    ) {
        if (events.isEmpty()) {
            return;
        }
        Map<FailureTransition, List<UUID>> targetsByTransition = events.stream()
                .collect(Collectors.groupingBy(
                        event -> failureTransition(event.retryCount(), failedAt),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                DecodedOutboxEvent::id,
                                Collectors.toList()
                        )
                ));

        String error = errorMessage(failure);
        for (Map.Entry<FailureTransition, List<UUID>> entry : targetsByTransition.entrySet()) {
            FailureTransition transition = entry.getKey();
            List<UUID> eventIds = entry.getValue();
            int updated = outboxEventRepository.markAllFailedIfClaimed(
                    eventIds,
                    claimId,
                    transition.expectedRetryCount(),
                    transition.targetStatus(),
                    transition.nextRetryAt(),
                    error,
                    failedAt
            );
            requireOwnership(updated, eventIds, claimId);
        }
    }

    private FailureTransition failureTransition(int retryCount, LocalDateTime failedAt) {
        if (retryPolicy.exhaustedAfter(retryCount)) {
            return new FailureTransition(retryCount, OutboxEventStatus.DEAD_LETTER, null);
        }
        return new FailureTransition(
                retryCount,
                OutboxEventStatus.FAILED,
                failedAt.plus(retryPolicy.delayAfter(retryCount))
        );
    }

    private void requireOwnership(int updated, UUID eventId, UUID claimId) {
        if (updated != 1) {
            throw new OutboxClaimOwnershipLostException(eventId, claimId);
        }
    }

    private void requireOwnership(int updated, List<UUID> eventIds, UUID claimId) {
        if (updated != eventIds.size()) {
            throw OutboxClaimOwnershipLostException.forGroup(eventIds, claimId);
        }
    }

    private String errorMessage(RuntimeException failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private record FailureTransition(
            int expectedRetryCount,
            OutboxEventStatus targetStatus,
            LocalDateTime nextRetryAt
    ) {
    }
}
