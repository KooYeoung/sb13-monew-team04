package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimBatch;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimHeartbeat;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimLease;
import com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimService;
import com.codeit.sb13.monew.activity.outbox.worker.config.OutboxWorkerProperties;
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한 polling batch의 claim부터 MongoDB projection과 최종 상태 저장까지 조율한다.
 *
 * <p>claim 직후 heartbeat를 시작하고 payload decode, RDB 현재 상태 batch 조회,
 * 이벤트별 MongoDB 반영을 순서대로 수행한다. MongoDB 반영이 성공한 뒤에만
 * {@code PROCESSED}로 변경하며, 처리 실패는 retry 정책에 맡긴다.</p>
 *
 * <p>heartbeat 또는 상태 저장에 문제가 생기면 새 이벤트 처리를 시작하지 않고
 * 남은 claim이 만료되도록 둔다. RDB와 MongoDB를 하나의 트랜잭션으로 묶지 않으므로
 * 전달 보장은 at-least-once이며 projection은 멱등해야 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxClaimService claimService;
    private final OutboxClaimHeartbeat claimHeartbeat;
    private final OutboxEventDecoder eventDecoder;
    private final OutboxProjectionSourceReader sourceReader;
    private final OutboxProjectionHandler projectionHandler;
    private final OutboxEventStateService eventStateService;
    private final OutboxWorkerProperties properties;
    private final Clock clock;

    /**
     * 처리 가능한 Outbox 이벤트를 한 batch claim해 순차 처리한다.
     *
     * <p>이 메서드의 RDB claim transaction은 MongoDB 처리 전에 종료된다. 여러
     * 인스턴스가 호출해도 각 실행은 서로 다른 claim batch를 받는다.</p>
     *
     * @return claim·처리·실패·미처리 건수를 계산할 수 있는 실행 결과
     */
    public OutboxWorkerResult runOnce() {
        OutboxClaimBatch batch = claimService.claim(
                properties.batchSize(),
                properties.claimLease()
        );
        if (batch.isEmpty()) {
            return OutboxWorkerResult.empty();
        }

        OutboxClaimLease lease;
        try {
            lease = claimHeartbeat.start(batch.claimId());
        } catch (RuntimeException e) {
            log.error(
                    "Outbox claim heartbeat를 시작하지 못했습니다. claimId={}",
                    batch.claimId(),
                    e
            );
            releaseUnstartedClaim(batch.claimId());
            return new OutboxWorkerResult(batch.events().size(), 0, 0);
        }
        try (lease) {
            return processClaimedBatch(batch, lease);
        }
    }

    private OutboxWorkerResult processClaimedBatch(
            OutboxClaimBatch batch,
            OutboxClaimLease lease
    ) {
        List<OutboxEvent> selected = batch.events();
        DecodeResult decodeResult = decodeEvents(selected, batch.claimId(), lease);
        List<DecodedOutboxEvent> decoded = decodeResult.events();
        if (decodeResult.stopped() || decoded.isEmpty()) {
            return new OutboxWorkerResult(selected.size(), 0, decodeResult.failed());
        }
        if (!claimHealthy(batch.claimId(), lease)) {
            return new OutboxWorkerResult(selected.size(), 0, decodeResult.failed());
        }

        ProjectionSourceBatch source;
        try {
            source = sourceReader.read(decoded);
        } catch (RuntimeException e) {
            int sourceFailures = recordSourceReadFailures(decoded, batch.claimId(), e);
            return new OutboxWorkerResult(
                    selected.size(),
                    0,
                    decodeResult.failed() + sourceFailures
            );
        }

        ProjectionResult projectionResult = projectEvents(
                decoded,
                source,
                batch.claimId(),
                lease
        );
        return new OutboxWorkerResult(
                selected.size(),
                projectionResult.processed(),
                decodeResult.failed() + projectionResult.failed()
        );
    }

    private DecodeResult decodeEvents(
            List<OutboxEvent> selected,
            UUID claimId,
            OutboxClaimLease lease
    ) {
        List<DecodedOutboxEvent> decoded = new ArrayList<>();
        int failed = 0;
        for (OutboxEvent event : selected) {
            if (!claimHealthy(claimId, lease)) {
                return new DecodeResult(decoded, failed, true);
            }
            try {
                decoded.add(eventDecoder.decode(event));
            } catch (RuntimeException e) {
                failed++;
                if (!recordFailure(
                        event.getId(),
                        claimId,
                        e,
                        LocalDateTime.now(clock)
                )) {
                    return new DecodeResult(decoded, failed, true);
                }
            }
        }
        return new DecodeResult(decoded, failed, false);
    }

    private int recordSourceReadFailures(
            List<DecodedOutboxEvent> decoded,
            UUID claimId,
            RuntimeException failure
    ) {
        int failed = 0;
        for (DecodedOutboxEvent event : decoded) {
            failed++;
            if (!recordFailure(
                    event.id(),
                    claimId,
                    failure,
                    LocalDateTime.now(clock)
            )) {
                break;
            }
        }
        return failed;
    }

    private ProjectionResult projectEvents(
            List<DecodedOutboxEvent> decoded,
            ProjectionSourceBatch source,
            UUID claimId,
            OutboxClaimLease lease
    ) {
        int processed = 0;
        int failed = 0;
        for (DecodedOutboxEvent event : decoded) {
            if (!claimHealthy(claimId, lease)) {
                break;
            }
            LocalDateTime processedAt = LocalDateTime.now(clock);
            try {
                projectionHandler.project(event, source, processedAt);
            } catch (RuntimeException e) {
                failed++;
                if (!recordFailure(event.id(), claimId, e, processedAt)) {
                    break;
                }
                continue;
            }

            if (!claimHealthy(claimId, lease)) {
                break;
            }
            try {
                eventStateService.markProcessed(event.id(), claimId, processedAt);
                processed++;
            } catch (RuntimeException e) {
                log.error(
                        "Outbox 처리 완료 상태를 저장하지 못했습니다. eventId={}, claimId={}",
                        event.id(),
                        claimId,
                        e
                );
                break;
            }
        }
        return new ProjectionResult(processed, failed);
    }

    private boolean claimHealthy(UUID claimId, OutboxClaimLease lease) {
        try {
            lease.verifyHealthy();
            return true;
        } catch (RuntimeException e) {
            log.error("Outbox claim lease를 유지하지 못해 처리를 중단합니다. claimId={}", claimId, e);
            return false;
        }
    }

    private void releaseUnstartedClaim(UUID claimId) {
        try {
            claimService.release(claimId);
        } catch (RuntimeException releaseFailure) {
            log.error(
                    "시작하지 못한 Outbox claim을 해제하지 못했습니다. claimId={}",
                    claimId,
                    releaseFailure
            );
        }
    }

    private boolean recordFailure(
            UUID eventId,
            UUID claimId,
            RuntimeException failure,
            LocalDateTime failedAt
    ) {
        try {
            eventStateService.markFailed(eventId, claimId, failure, failedAt);
            return true;
        } catch (RuntimeException stateFailure) {
            log.error(
                    "Outbox 처리 실패 상태를 저장하지 못했습니다. eventId={}, claimId={}",
                    eventId,
                    claimId,
                    stateFailure
            );
            return false;
        }
    }

    /**
     * decode 단계에서 다음 단계로 전달할 이벤트와 처리 중단 상태를 보관한다.
     *
     * @param events decode에 성공한 이벤트
     * @param failed decode 실패로 상태 전이를 시도한 이벤트 수
     * @param stopped lease 또는 실패 상태 저장 문제로 처리를 중단했으면 {@code true}
     */
    private record DecodeResult(
            List<DecodedOutboxEvent> events,
            int failed,
            boolean stopped
    ) {
        private DecodeResult {
            events = List.copyOf(events);
        }
    }

    /**
     * MongoDB projection 단계의 처리 결과다.
     *
     * @param processed projection과 완료 상태 저장에 성공한 이벤트 수
     * @param failed projection 실패로 상태 전이를 시도한 이벤트 수
     */
    private record ProjectionResult(int processed, int failed) {
    }
}
