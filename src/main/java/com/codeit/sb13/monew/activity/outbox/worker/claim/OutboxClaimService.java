package com.codeit.sb13.monew.activity.outbox.worker.claim;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 worker 인스턴스에 처리 가능한 Outbox 이벤트를 중복 없이 분배한다.
 *
 * <p>후보 row를 {@code FOR UPDATE SKIP LOCKED}로 잠근 뒤 같은 SQL에서 batch
 * {@code claimId}와 PostgreSQL 시각 기반 lease를 기록한다. 이 짧은 트랜잭션은
 * claim 결과를 조회한 뒤 종료되며 RDB source 조회나 MongoDB 쓰기를 포함하지 않는다.</p>
 *
 * <p>worker 장애로 heartbeat가 중단되면 {@code claimUntil}이 지난 row를 다른
 * 실행이 새 UUID로 회수할 수 있다. 이 구조는 exactly-once나 fencing이 아니라
 * at-least-once 전달을 제공한다.</p>
 */
@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT event.id
                FROM outbox_events event
                WHERE (event.status = 'PENDING'
                       OR (event.status = 'FAILED'
                           AND event.next_retry_at <= CURRENT_TIMESTAMP))
                  AND (event.claim_id IS NULL
                       OR event.claim_until IS NULL
                       OR event.claim_until <= CURRENT_TIMESTAMP)
                ORDER BY event.created_at ASC, event.id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE outbox_events event
            SET claim_id = :claimId,
                claimed_at = CURRENT_TIMESTAMP,
                claim_until = CURRENT_TIMESTAMP
                    + (:leaseMilliseconds * INTERVAL '1 millisecond'),
                updated_at = CURRENT_TIMESTAMP
            FROM candidates
            WHERE event.id = candidates.id
            """;

    private static final String RENEW_SQL = """
            UPDATE outbox_events
            SET claim_until = CURRENT_TIMESTAMP
                    + (:leaseMilliseconds * INTERVAL '1 millisecond'),
                updated_at = CURRENT_TIMESTAMP
            WHERE claim_id = :claimId
              AND (status = 'PENDING' OR status = 'FAILED')
            """;

    private static final String RELEASE_SQL = """
            UPDATE outbox_events
            SET claim_id = NULL,
                claimed_at = NULL,
                claim_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE claim_id = :claimId
              AND (status = 'PENDING' OR status = 'FAILED')
            """;

    private final EntityManager entityManager;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * 처리 가능한 이벤트를 최대 batch 크기만큼 원자적으로 선점한다.
     *
     * <p>{@code PENDING} 또는 재시도 시각이 지난 {@code FAILED} 중 claim이 없거나
     * 만료된 row만 대상으로 하며 {@code createdAt}, ID 순으로 선택한다.</p>
     *
     * @param batchSize 한 번에 선점할 최대 이벤트 수
     * @param leaseDuration 최초 claim 유효 시간
     * @return 실행 UUID와 해당 UUID로 선점한 이벤트 목록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxClaimBatch claim(int batchSize, Duration leaseDuration) {
        UUID claimId = UUID.randomUUID();
        int claimed = entityManager.createNativeQuery(CLAIM_SQL)
                .setParameter("batchSize", batchSize)
                .setParameter("claimId", claimId)
                .setParameter("leaseMilliseconds", leaseDuration.toMillis())
                .executeUpdate();
        if (claimed == 0) {
            return new OutboxClaimBatch(claimId, List.of());
        }

        entityManager.clear();
        List<OutboxEvent> events =
                outboxEventRepository.findAllByClaimIdOrderByCreatedAtAscIdAsc(claimId);
        return new OutboxClaimBatch(claimId, events);
    }

    /**
     * 현재 UUID가 소유한 미완료 이벤트들의 lease를 PostgreSQL 현재 시각부터 연장한다.
     *
     * @param claimId 연장할 batch 실행 UUID
     * @param leaseDuration 갱신 시점부터 적용할 lease 시간
     * @return lease가 연장된 이벤트 row 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int renew(UUID claimId, Duration leaseDuration) {
        return entityManager.createNativeQuery(RENEW_SQL)
                .setParameter("claimId", claimId)
                .setParameter("leaseMilliseconds", leaseDuration.toMillis())
                .executeUpdate();
    }

    /**
     * MongoDB 처리를 시작하지 못한 batch의 claim을 즉시 해제한다.
     *
     * <p>해당 UUID가 여전히 소유한 미완료 이벤트만 갱신하므로 이미 회수되거나
     * 종결된 이벤트에는 영향을 주지 않는다.</p>
     *
     * @param claimId 해제할 batch 실행 UUID
     * @return claim이 해제된 이벤트 row 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int release(UUID claimId) {
        return entityManager.createNativeQuery(RELEASE_SQL)
                .setParameter("claimId", claimId)
                .executeUpdate();
    }
}
