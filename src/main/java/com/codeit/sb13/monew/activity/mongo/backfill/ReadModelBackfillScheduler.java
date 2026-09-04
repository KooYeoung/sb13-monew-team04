package com.codeit.sb13.monew.activity.mongo.backfill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** MongoDB와 초기 투영 설정이 모두 활성화된 경우에만 checkpoint 작업을 polling한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "monew.mongodb",
        name = {"enabled", "worker.enabled", "backfill.enabled"},
        havingValue = "true"
)
public class ReadModelBackfillScheduler {

    private final ReadModelBackfillWorker worker;

    @Scheduled(fixedDelayString = "${monew.mongodb.backfill.fixed-delay-ms:1000}")
    public void projectInitialReadModel() {
        ReadModelBackfillResult result = worker.runOnce();
        if (result.workType() != ReadModelBackfillWorkType.NONE) {
            log.info(
                    "Read Model 초기 투영 실행 완료. runId={}, type={}, selected={}, "
                            + "processed={}, failed={}, verificationMatched={}",
                    result.runId(),
                    result.workType(),
                    result.selected(),
                    result.processed(),
                    result.failed(),
                    result.verificationMatched()
            );
        }
    }
}
