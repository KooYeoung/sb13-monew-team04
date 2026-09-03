package com.codeit.sb13.monew.activity.outbox.repository;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Outbox 이벤트 저장과 claim 소유권 기반 상태 변경을 제공하는 저장소다.
 *
 * <p>worker 상태 갱신은 항상 이벤트 ID와 claim ID를 함께 비교한다. 반환된 갱신
 * row 수가 {@code 0}이면 이벤트가 다른 worker에 회수됐거나 이미 종결된 것이므로
 * 현재 실행은 더 이상 해당 이벤트의 소유자가 아니다.</p>
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 한 실행이 claim한 이벤트를 결정적인 처리 순서로 조회한다.
     *
     * @param claimId polling batch를 식별하는 실행 UUID
     * @return 생성 시각과 이벤트 ID 순으로 정렬된 claim 이벤트
     */
    List<OutboxEvent> findAllByClaimIdOrderByCreatedAtAscIdAsc(UUID claimId);

    /**
     * 현재 실행이 소유한 이벤트를 조회한다.
     *
     * @param eventId 이벤트 식별자
     * @param claimId 현재 실행의 claim UUID
     * @return 두 식별자가 모두 일치하는 이벤트
     */
    Optional<OutboxEvent> findByIdAndClaimId(UUID eventId, UUID claimId);

    /**
     * 현재 claim 소유자일 때만 이벤트를 완료하고 claim과 retry 정보를 정리한다.
     *
     * @param eventId 완료할 이벤트 식별자
     * @param claimId 현재 실행의 claim UUID
     * @param processedAt MongoDB projection 완료 시각
     * @return 갱신 성공 시 {@code 1}, 소유권이 없으면 {@code 0}
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent event
            SET event.status = com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus.PROCESSED,
                event.processedAt = :processedAt,
                event.nextRetryAt = null,
                event.lastError = null,
                event.claimId = null,
                event.claimedAt = null,
                event.claimUntil = null,
                event.updatedAt = :processedAt
            WHERE event.id = :eventId
              AND event.claimId = :claimId
              AND (event.status = com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus.PENDING
                   OR event.status = com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus.FAILED)
            """)
    int markProcessedIfClaimed(
            @Param("eventId") UUID eventId,
            @Param("claimId") UUID claimId,
            @Param("processedAt") LocalDateTime processedAt
    );

    /**
     * 현재 claim과 예상 retry 횟수가 일치할 때 실패 상태를 원자적으로 기록한다.
     *
     * @param eventId 실패한 이벤트 식별자
     * @param claimId 현재 실행의 claim UUID
     * @param expectedRetryCount 처리 시작 시 관찰한 retry 횟수
     * @param targetStatus 다음 상태인 {@link OutboxEventStatus#FAILED} 또는 {@link OutboxEventStatus#DEAD_LETTER}
     * @param nextRetryAt 다음 처리 가능 시각, Dead Letter이면 {@code null}
     * @param lastError 저장할 실패 원인
     * @param failedAt 실패 상태를 기록한 시각
     * @return 갱신 성공 시 {@code 1}, 소유권 또는 retry 횟수가 달라졌으면 {@code 0}
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent event
            SET event.status = :targetStatus,
                event.retryCount = event.retryCount + 1,
                event.nextRetryAt = :nextRetryAt,
                event.processedAt = null,
                event.lastError = :lastError,
                event.claimId = null,
                event.claimedAt = null,
                event.claimUntil = null,
                event.updatedAt = :failedAt
            WHERE event.id = :eventId
              AND event.claimId = :claimId
              AND event.retryCount = :expectedRetryCount
              AND (event.status = com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus.PENDING
                   OR event.status = com.codeit.sb13.monew.activity.outbox.domain.OutboxEventStatus.FAILED)
            """)
    int markFailedIfClaimed(
            @Param("eventId") UUID eventId,
            @Param("claimId") UUID claimId,
            @Param("expectedRetryCount") int expectedRetryCount,
            @Param("targetStatus") OutboxEventStatus targetStatus,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("failedAt") LocalDateTime failedAt
    );
}
