package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEvent;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한 polling batch의 claim부터 MongoDB projection과 최종 상태 저장까지 조율한다.
 *
 * <p>claim 직후 heartbeat를 시작하고 payload decode, RDB 현재 상태 batch 조회,
 * MongoDB 반영을 순서대로 수행한다. 같은 count 대상은 polling batch 안에서
 * 한 번만 반영하고, 성공한 뒤에만 그룹 전체를 {@code PROCESSED}로 변경한다.</p>
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

        List<ProcessingGroup> processingGroups = groupForProcessing(decoded);

        ProjectionSourceBatch source;
        try {
            source = sourceReader.read(processingGroups.stream()
                    .map(ProcessingGroup::projectionEvent)
                    .toList());
        } catch (RuntimeException e) {
            int sourceFailures = recordSourceReadFailures(
                    processingGroups,
                    batch.claimId(),
                    e
            );
            return new OutboxWorkerResult(
                    selected.size(),
                    0,
                    decodeResult.failed() + sourceFailures
            );
        }

        ProjectionResult projectionResult = projectGroups(
                processingGroups,
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

    /**
     * count 변경 이벤트만 event type과 snapshot 대상 ID 기준으로 묶는다.
     *
     * <p>일반 이벤트는 각각 독립된 그룹을 유지하고, 그룹 자체는 첫 이벤트가 나타난
     * 순서를 따른다. count 그룹의 projection에는 가장 높은 version의 이벤트를 사용해
     * 해당 그룹보다 낮은 version의 지연 쓰기를 MongoDB CAS가 차단할 수 있게 한다.</p>
     */
    private List<ProcessingGroup> groupForProcessing(List<DecodedOutboxEvent> events) {
        Map<ProcessingKey, List<DecodedOutboxEvent>> grouped = events.stream()
                .collect(Collectors.groupingBy(
                        ProcessingKey::from,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.values().stream()
                .map(ProcessingGroup::new)
                .toList();
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
                if (!recordFailure(
                        event.getId(),
                        claimId,
                        e,
                        LocalDateTime.now(clock)
                )) {
                    return new DecodeResult(decoded, failed, true);
                }
                failed++;
            }
        }
        return new DecodeResult(decoded, failed, false);
    }

    private int recordSourceReadFailures(
            List<ProcessingGroup> groups,
            UUID claimId,
            RuntimeException failure
    ) {
        int failed = 0;
        for (ProcessingGroup group : groups) {
            if (!recordFailure(
                    group,
                    claimId,
                    failure,
                    LocalDateTime.now(clock)
            )) {
                break;
            }
            failed += group.size();
        }
        return failed;
    }

    private ProjectionResult projectGroups(
            List<ProcessingGroup> groups,
            ProjectionSourceBatch source,
            UUID claimId,
            OutboxClaimLease lease
    ) {
        int processed = 0;
        int failed = 0;
        for (ProcessingGroup group : groups) {
            if (!claimHealthy(claimId, lease)) {
                break;
            }
            LocalDateTime processedAt = LocalDateTime.now(clock);
            try {
                projectionHandler.project(group.projectionEvent(), source, processedAt);
            } catch (RuntimeException e) {
                if (!recordFailure(group, claimId, e, processedAt)) {
                    break;
                }
                failed += group.size();
                continue;
            }

            if (!claimHealthy(claimId, lease)) {
                break;
            }
            try {
                markProcessed(group, claimId, processedAt);
                processed += group.size();
            } catch (RuntimeException e) {
                log.error(
                        "Outbox 처리 완료 상태를 저장하지 못했습니다. eventIds={}, claimId={}",
                        group.eventIds(),
                        claimId,
                        e
                );
                break;
            }
        }
        return new ProjectionResult(processed, failed);
    }

    private void markProcessed(
            ProcessingGroup group,
            UUID claimId,
            LocalDateTime processedAt
    ) {
        if (group.size() == 1) {
            eventStateService.markProcessed(
                    group.projectionEvent().id(),
                    claimId,
                    processedAt
            );
            return;
        }
        eventStateService.markProcessed(group.eventIds(), claimId, processedAt);
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

    private boolean recordFailure(
            ProcessingGroup group,
            UUID claimId,
            RuntimeException failure,
            LocalDateTime failedAt
    ) {
        if (group.size() == 1) {
            return recordFailure(
                    group.projectionEvent().id(),
                    claimId,
                    failure,
                    failedAt
            );
        }
        try {
            eventStateService.markFailed(group.events(), claimId, failure, failedAt);
            return true;
        } catch (RuntimeException stateFailure) {
            log.error(
                    "Outbox 그룹 처리 실패 상태를 저장하지 못했습니다. eventIds={}, claimId={}",
                    group.eventIds(),
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
     * @param failed decode 실패 상태 저장까지 완료한 이벤트 수
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
     * @param failed projection 실패 상태 저장까지 완료한 이벤트 수
     */
    private record ProjectionResult(int processed, int failed) {
    }

    /** event type과 대상이 같은 count 신호 또는 독립된 일반 이벤트의 처리 단위다. */
    private record ProcessingGroup(
            DecodedOutboxEvent projectionEvent,
            List<DecodedOutboxEvent> events
    ) {
        private ProcessingGroup(List<DecodedOutboxEvent> events) {
            this(
                    events.stream()
                            .max(Comparator.comparingLong(
                                    DecodedOutboxEvent::projectionVersion))
                            .orElseThrow(),
                    List.copyOf(events)
            );
        }

        private int size() {
            return events.size();
        }

        private List<UUID> eventIds() {
            return events.stream().map(DecodedOutboxEvent::id).toList();
        }
    }

    /** count 이벤트에는 공유 키를, 일반 이벤트에는 고유 event ID를 부여한다. */
    private record ProcessingKey(
            OutboxEventType eventType,
            UUID aggregateId,
            UUID individualEventId
    ) {
        private static ProcessingKey from(DecodedOutboxEvent event) {
            return new ProcessingKey(
                    event.eventType(),
                    event.aggregateId(),
                    event.eventType().isCountChanged() ? null : event.id()
            );
        }
    }
}
