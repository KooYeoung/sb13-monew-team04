package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.activity.outbox.service.OutboxProjectionVersionAllocator;
import com.codeit.sb13.monew.activity.outbox.worker.source.OutboxProjectionSourceReader;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillCheckpointNotFoundException;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillClaimOwnershipLostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 run의 checkpoint를 잠그고 projection page 또는 검증 작업을 단일 실행에 분배한다.
 *
 * <p>projection clock을 원본 조회 전에 잠그므로 이후 commit되는 도메인 이벤트는 항상
 * 더 높은 version을 받는다. MongoDB 쓰기는 이 트랜잭션 밖에서 수행한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ReadModelBackfillClaimService {

    private final ReadModelBackfillRunRepository repository;
    private final ReadModelBackfillRunInitializer initializer;
    private final ReadModelBackfillScanner scanner;
    private final OutboxProjectionVersionAllocator versionAllocator;
    private final OutboxProjectionSourceReader sourceReader;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReadModelBackfillWork claim(
            UUID runId,
            int batchSize,
            Duration leaseDuration
    ) {
        LocalDateTime now = repository.currentTime();
        ensureCheckpoint(runId, now);
        ReadModelBackfillRun run = repository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ReadModelBackfillCheckpointNotFoundException(runId));
        if (!run.canClaim(now)) {
            return ReadModelBackfillWork.none(runId);
        }

        UUID claimId = UUID.randomUUID();
        LocalDateTime claimUntil = now.plus(leaseDuration);
        if (run.requiresVerification()) {
            run.claimVerification(claimId, now, claimUntil);
            return ReadModelBackfillWork.verification(runId, claimId);
        }

        while (true) {
            long projectionVersion = versionAllocator.allocate();
            InitialProjectionPage page = scanner.scan(
                    run.getStage(),
                    run.getLastProcessedId(),
                    run.getPendingLastId(),
                    batchSize,
                    projectionVersion
            );
            if (page.isEmpty()) {
                if (run.getPendingLastId() != null) {
                    run.skipPendingRange(now);
                    continue;
                }
                run.advanceStage(now);
                if (run.requiresVerification()) {
                    run.claimVerification(claimId, now, claimUntil);
                    return ReadModelBackfillWork.verification(runId, claimId);
                }
                continue;
            }

            run.preparePendingRange(page.lastSourceRowId(), now);
            run.claimProjection(claimId, now, claimUntil);
            ProjectionSourceBatch source = sourceReader.read(page.events());
            return new ReadModelBackfillWork(
                    ReadModelBackfillWorkType.PROJECT,
                    runId,
                    claimId,
                    run.getStage(),
                    page.events(),
                    source
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID runId, UUID claimId, int count) {
        ReadModelBackfillRun run = ownedRun(runId, claimId);
        run.completeBatch(claimId, count, repository.currentTime());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID runId, UUID claimId, Throwable failure) {
        ReadModelBackfillRun run = ownedRun(runId, claimId);
        run.fail(claimId, failure, repository.currentTime());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVerification(
            UUID runId,
            UUID claimId,
            String report,
            boolean matched
    ) {
        ReadModelBackfillRun run = ownedRun(runId, claimId);
        run.recordVerification(claimId, report, matched, repository.currentTime());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int renew(UUID runId, UUID claimId, Duration leaseDuration) {
        LocalDateTime now = repository.currentTime();
        return repository.renew(runId, claimId, now.plus(leaseDuration), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID runId, UUID claimId) {
        ReadModelBackfillRun run = ownedRun(runId, claimId);
        run.release(claimId, repository.currentTime());
    }

    private void ensureCheckpoint(UUID runId, LocalDateTime now) {
        try {
            initializer.ensureExists(runId, now);
        } catch (DataIntegrityViolationException ignored) {
            // 다른 인스턴스가 같은 run-id를 먼저 만들었다. 아래 row lock 조회로 합류한다.
        }
    }

    private ReadModelBackfillRun ownedRun(UUID runId, UUID claimId) {
        ReadModelBackfillRun run = repository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ReadModelBackfillClaimOwnershipLostException(runId, claimId));
        if (!claimId.equals(run.getClaimId())) {
            throw new ReadModelBackfillClaimOwnershipLostException(runId, claimId);
        }
        return run;
    }
}
