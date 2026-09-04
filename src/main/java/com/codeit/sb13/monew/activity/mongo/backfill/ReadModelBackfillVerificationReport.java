package com.codeit.sb13.monew.activity.mongo.backfill;

import java.util.EnumMap;
import java.util.Map;

/**
 * 초기 투영 완료 후 RDB 원본과 MongoDB Read Model을 활동 유형별로 대조한 결과다.
 *
 * @param stages 활동 유형별 건수 및 불일치 내역
 */
public record ReadModelBackfillVerificationReport(
        Map<ReadModelBackfillStage, StageVerification> stages
) {

    public ReadModelBackfillVerificationReport {
        EnumMap<ReadModelBackfillStage, StageVerification> copy =
                new EnumMap<>(ReadModelBackfillStage.class);
        copy.putAll(stages);
        stages = Map.copyOf(copy);
    }

    public boolean matched() {
        return stages.size() == ReadModelBackfillStage.values().length
                && stages.values().stream().allMatch(StageVerification::matched);
    }

    /**
     * 한 활동 유형에서 기대한 활동 문서와 실제 노출 문서, 참조 snapshot을 비교한 결과다.
     *
     * @param expectedActivities 현재 RDB 상태에서 노출되어야 하는 활동 수
     * @param actualVisibleActivities MongoDB에서 실제 노출 중인 해당 유형 활동 수
     * @param missingOrInvalidActivities 누락되었거나 핵심 필드가 다른 활동 수
     * @param snapshotChecks 활동이 참조하는 snapshot 확인 횟수
     * @param snapshotMismatches 누락·비노출·집계값 불일치 snapshot 수
     */
    public record StageVerification(
            long expectedActivities,
            long actualVisibleActivities,
            long missingOrInvalidActivities,
            long snapshotChecks,
            long snapshotMismatches
    ) {

        public boolean matched() {
            return expectedActivities == actualVisibleActivities
                    && missingOrInvalidActivities == 0
                    && snapshotMismatches == 0;
        }
    }
}
