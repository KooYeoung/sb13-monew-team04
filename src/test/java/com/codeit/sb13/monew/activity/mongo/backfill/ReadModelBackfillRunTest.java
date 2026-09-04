package com.codeit.sb13.monew.activity.mongo.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadModelBackfillRunTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);

    @Test
    @DisplayName("실패한 page는 완료 cursor를 이동하지 않고 같은 범위를 재개한다")
    void failedPageKeepsPendingRangeForResume() {
        UUID runId = UUID.randomUUID();
        UUID pendingLastId = UUID.randomUUID();
        UUID firstClaim = UUID.randomUUID();
        ReadModelBackfillRun run = ReadModelBackfillRun.start(runId, now);
        run.preparePendingRange(pendingLastId, now);
        run.claimProjection(firstClaim, now, now.plusMinutes(5));

        run.fail(firstClaim, new IllegalStateException("mongo failed"), now.plusSeconds(1));

        assertThat(run.getLastProcessedId()).isNull();
        assertThat(run.getPendingLastId()).isEqualTo(pendingLastId);
        assertThat(run.getRetryCount()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(ReadModelBackfillStatus.FAILED);

        UUID retryClaim = UUID.randomUUID();
        run.claimProjection(retryClaim, now.plusSeconds(2), now.plusMinutes(5));
        run.completeBatch(retryClaim, 3, now.plusSeconds(3));

        assertThat(run.getLastProcessedId()).isEqualTo(pendingLastId);
        assertThat(run.getPendingLastId()).isNull();
        assertThat(run.getProcessedCount()).isEqualTo(3);
        assertThat(run.getStatus()).isEqualTo(ReadModelBackfillStatus.PENDING);
    }

    @Test
    @DisplayName("마지막 stage가 끝나면 검증으로 전환하고 일치한 run은 다시 claim하지 않는다")
    void completedRunIsNotClaimable() {
        UUID runId = UUID.randomUUID();
        ReadModelBackfillRun run = ReadModelBackfillRun.start(runId, now);
        for (int i = 0; i < ReadModelBackfillStage.values().length; i++) {
            run.advanceStage(now.plusSeconds(i));
        }
        UUID claimId = UUID.randomUUID();
        run.claimVerification(claimId, now.plusMinutes(1), now.plusMinutes(6));

        run.recordVerification(claimId, "{\"matched\":true}", true, now.plusMinutes(2));

        assertThat(run.getStatus()).isEqualTo(ReadModelBackfillStatus.COMPLETED);
        assertThat(run.canClaim(now.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("검증 불일치는 보고서를 남기고 첫 stage부터 재투영한다")
    void verificationMismatchRestartsProjection() {
        UUID runId = UUID.randomUUID();
        ReadModelBackfillRun run = ReadModelBackfillRun.start(runId, now);
        for (int i = 0; i < ReadModelBackfillStage.values().length; i++) {
            run.advanceStage(now.plusSeconds(i));
        }
        UUID claimId = UUID.randomUUID();
        run.claimVerification(claimId, now.plusMinutes(1), now.plusMinutes(6));

        run.recordVerification(claimId, "{\"mismatch\":1}", false, now.plusMinutes(2));

        assertThat(run.getStatus()).isEqualTo(ReadModelBackfillStatus.VERIFICATION_FAILED);
        assertThat(run.getStage()).isEqualTo(ReadModelBackfillStage.SUBSCRIPTION);
        assertThat(run.getLastProcessedId()).isNull();
        assertThat(run.getPendingLastId()).isNull();
        assertThat(run.getProcessedCount()).isZero();
        assertThat(run.requiresVerification()).isFalse();
        assertThat(run.canClaim(now.plusMinutes(3))).isTrue();
    }
}
