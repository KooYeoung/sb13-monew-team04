package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.activity.outbox.worker.OutboxProjectionHandler;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillReportSerializationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 초기 투영 page claim, MongoDB 반영, checkpoint 갱신과 최종 정합성 검증을 조율한다.
 *
 * <p>RDB page 조회 transaction은 MongoDB 반영 전에 끝난다. 따라서 전달 보장은
 * at-least-once이며, 동일 page 재처리는 결정적 문서 ID와 projection version CAS로
 * 중복 및 오래된 덮어쓰기를 막는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadModelBackfillWorker {

    private final ReadModelBackfillClaimService claimService;
    private final ReadModelBackfillHeartbeat heartbeat;
    private final OutboxProjectionHandler projectionHandler;
    private final ReadModelBackfillVerifier verifier;
    private final ReadModelBackfillProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 설정된 run-id에서 처리할 page 하나 또는 최종 검증 작업 하나를 수행한다. */
    public ReadModelBackfillResult runOnce() {
        UUID runId = properties.runId();
        ReadModelBackfillWork work = claimService.claim(
                runId,
                properties.batchSize(),
                properties.claimLease()
        );
        if (work.isEmpty()) {
            return ReadModelBackfillResult.empty(runId);
        }

        ReadModelBackfillLease lease;
        try {
            lease = heartbeat.start(runId, work.claimId());
        } catch (RuntimeException failure) {
            log.error(
                    "Read Model 초기 투영 heartbeat를 시작하지 못했습니다. runId={}, claimId={}",
                    runId,
                    work.claimId(),
                    failure
            );
            releaseSafely(work, failure);
            return failedResult(work);
        }

        try (lease) {
            return switch (work.type()) {
                case PROJECT -> project(work, lease);
                case VERIFY -> verify(work, lease);
                case NONE -> ReadModelBackfillResult.empty(runId);
            };
        }
    }

    private ReadModelBackfillResult project(
            ReadModelBackfillWork work,
            ReadModelBackfillLease lease
    ) {
        int processed = 0;
        try {
            for (InitialProjectionEvent event : work.events()) {
                lease.verifyHealthy();
                projectionHandler.project(event, work.source(), LocalDateTime.now(clock));
                processed++;
            }
            lease.verifyHealthy();
            claimService.markProcessed(work.runId(), work.claimId(), work.events().size());
            return ReadModelBackfillResult.projection(
                    work.runId(), work.events().size(), processed, false);
        } catch (RuntimeException failure) {
            log.error(
                    "Read Model 초기 투영 page 처리에 실패했습니다. runId={}, claimId={}, stage={}",
                    work.runId(),
                    work.claimId(),
                    work.stage(),
                    failure
            );
            markFailedSafely(work, failure);
            return ReadModelBackfillResult.projection(
                    work.runId(), work.events().size(), processed, true);
        }
    }

    private ReadModelBackfillResult verify(
            ReadModelBackfillWork work,
            ReadModelBackfillLease lease
    ) {
        try {
            lease.verifyHealthy();
            ReadModelBackfillVerificationReport report = verifier.verify(
                    properties.batchSize(), lease::verifyHealthy);
            String reportJson = serializeReport(work.runId(), report);
            lease.verifyHealthy();
            claimService.recordVerification(
                    work.runId(), work.claimId(), reportJson, report.matched());
            return ReadModelBackfillResult.verification(
                    work.runId(), report.matched(), false);
        } catch (RuntimeException failure) {
            log.error(
                    "Read Model 초기 투영 정합성 검증에 실패했습니다. runId={}, claimId={}",
                    work.runId(),
                    work.claimId(),
                    failure
            );
            markFailedSafely(work, failure);
            return ReadModelBackfillResult.verification(work.runId(), false, true);
        }
    }

    private String serializeReport(
            UUID runId,
            ReadModelBackfillVerificationReport report
    ) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JacksonException failure) {
            throw new ReadModelBackfillReportSerializationException(runId, failure);
        }
    }

    private void markFailedSafely(ReadModelBackfillWork work, RuntimeException failure) {
        try {
            claimService.markFailed(work.runId(), work.claimId(), failure);
        } catch (RuntimeException stateFailure) {
            log.error(
                    "Read Model 초기 투영 실패 상태를 저장하지 못했습니다. runId={}, claimId={}",
                    work.runId(),
                    work.claimId(),
                    stateFailure
            );
        }
    }

    private void releaseSafely(ReadModelBackfillWork work, RuntimeException failure) {
        try {
            claimService.release(work.runId(), work.claimId());
        } catch (RuntimeException releaseFailure) {
            log.error(
                    "시작하지 못한 Read Model 초기 투영 claim을 해제하지 못했습니다. "
                            + "runId={}, claimId={}",
                    work.runId(),
                    work.claimId(),
                    releaseFailure
            );
            failure.addSuppressed(releaseFailure);
        }
    }

    private ReadModelBackfillResult failedResult(ReadModelBackfillWork work) {
        return work.type() == ReadModelBackfillWorkType.VERIFY
                ? ReadModelBackfillResult.verification(work.runId(), false, true)
                : ReadModelBackfillResult.projection(
                        work.runId(), work.events().size(), 0, true);
    }
}
