package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillClaimOwnershipLostException;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 초기 투영 실행의 stage, cursor, claim과 검증 결과를 보존하는 checkpoint다.
 *
 * <p>완료 cursor는 MongoDB batch 전체가 성공한 뒤에만 이동한다. 처리 중인 범위는
 * pending cursor로 별도 보관해 실패하거나 lease가 만료되면 같은 범위를 다시 실행한다.</p>
 */
@Entity
@Getter
@Table(name = "read_model_backfill_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadModelBackfillRun {

    @Id
    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 40)
    private ReadModelBackfillStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReadModelBackfillStatus status;

    @Column(name = "last_processed_id")
    private UUID lastProcessedId;

    @Column(name = "pending_last_id")
    private UUID pendingLastId;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "claim_until")
    private LocalDateTime claimUntil;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "verification_report", columnDefinition = "TEXT")
    private String verificationReport;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ReadModelBackfillRun(UUID runId, LocalDateTime now) {
        this.runId = runId;
        this.stage = ReadModelBackfillStage.SUBSCRIPTION;
        this.status = ReadModelBackfillStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ReadModelBackfillRun start(UUID runId, LocalDateTime now) {
        return new ReadModelBackfillRun(runId, now);
    }

    public boolean canClaim(LocalDateTime now) {
        if (status == ReadModelBackfillStatus.COMPLETED) {
            return false;
        }
        return claimId == null || claimUntil == null || !claimUntil.isAfter(now);
    }

    public boolean requiresVerification() {
        return status == ReadModelBackfillStatus.VERIFYING;
    }

    public void preparePendingRange(UUID lastId, LocalDateTime now) {
        requireProjectionState("preparePendingRange");
        if (pendingLastId == null) {
            pendingLastId = lastId;
        }
        updatedAt = now;
    }

    public void claimProjection(
            UUID newClaimId,
            LocalDateTime now,
            LocalDateTime until
    ) {
        requireClaimable(now, "claimProjection");
        if (pendingLastId == null) {
            throw new ReadModelBackfillStateException(runId, status.name(), "claimWithoutRange");
        }
        claimId = newClaimId;
        claimedAt = now;
        claimUntil = until;
        status = ReadModelBackfillStatus.RUNNING;
        updatedAt = now;
    }

    public void claimVerification(
            UUID newClaimId,
            LocalDateTime now,
            LocalDateTime until
    ) {
        requireClaimable(now, "claimVerification");
        if (!requiresVerification()) {
            throw new ReadModelBackfillStateException(runId, status.name(), "claimVerification");
        }
        claimId = newClaimId;
        claimedAt = now;
        claimUntil = until;
        status = ReadModelBackfillStatus.VERIFYING;
        updatedAt = now;
    }

    public void completeBatch(UUID currentClaimId, int batchCount, LocalDateTime now) {
        requireOwnership(currentClaimId);
        lastProcessedId = pendingLastId;
        pendingLastId = null;
        processedCount += batchCount;
        status = ReadModelBackfillStatus.PENDING;
        lastError = null;
        clearClaim();
        updatedAt = now;
    }

    public void skipPendingRange(LocalDateTime now) {
        requireProjectionState("skipPendingRange");
        lastProcessedId = pendingLastId;
        pendingLastId = null;
        status = ReadModelBackfillStatus.PENDING;
        clearClaim();
        updatedAt = now;
    }

    public void advanceStage(LocalDateTime now) {
        requireProjectionState("advanceStage");
        if (pendingLastId != null) {
            throw new ReadModelBackfillStateException(runId, status.name(), "advanceWithPendingRange");
        }
        stage.next().ifPresentOrElse(next -> {
            stage = next;
            lastProcessedId = null;
            status = ReadModelBackfillStatus.PENDING;
        }, () -> status = ReadModelBackfillStatus.VERIFYING);
        clearClaim();
        updatedAt = now;
    }

    public void fail(UUID currentClaimId, Throwable failure, LocalDateTime now) {
        requireOwnership(currentClaimId);
        retryCount++;
        lastError = failureMessage(failure);
        status = ReadModelBackfillStatus.FAILED;
        clearClaim();
        updatedAt = now;
    }

    public void recordVerification(
            UUID currentClaimId,
            String report,
            boolean matched,
            LocalDateTime now
    ) {
        requireOwnership(currentClaimId);
        verificationReport = report;
        verifiedAt = now;
        if (matched) {
            status = ReadModelBackfillStatus.COMPLETED;
            lastError = null;
        } else {
            stage = ReadModelBackfillStage.SUBSCRIPTION;
            status = ReadModelBackfillStatus.VERIFICATION_FAILED;
            lastProcessedId = null;
            pendingLastId = null;
            processedCount = 0;
            lastError = "RDB와 MongoDB Read Model 정합성 검증 불일치";
        }
        clearClaim();
        updatedAt = now;
    }

    public void release(UUID currentClaimId, LocalDateTime now) {
        requireOwnership(currentClaimId);
        status = requiresVerification()
                ? ReadModelBackfillStatus.VERIFICATION_FAILED
                : ReadModelBackfillStatus.FAILED;
        clearClaim();
        updatedAt = now;
    }

    private void requireProjectionState(String operation) {
        if (status == ReadModelBackfillStatus.COMPLETED || requiresVerification()) {
            throw new ReadModelBackfillStateException(runId, status.name(), operation);
        }
    }

    private void requireClaimable(LocalDateTime now, String operation) {
        if (!canClaim(now)) {
            throw new ReadModelBackfillStateException(runId, status.name(), operation);
        }
    }

    private void requireOwnership(UUID currentClaimId) {
        if (claimId == null || !claimId.equals(currentClaimId)) {
            throw new ReadModelBackfillClaimOwnershipLostException(runId, currentClaimId);
        }
    }

    private void clearClaim() {
        claimId = null;
        claimedAt = null;
        claimUntil = null;
    }

    private String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
    }
}
