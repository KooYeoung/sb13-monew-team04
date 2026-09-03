package com.codeit.sb13.monew.activity.outbox.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 설정된 fixed delay마다 Outbox polling을 시작하는 스케줄러다.
 *
 * <p>MongoDB와 worker 활성화 설정이 모두 {@code true}일 때만 생성된다. 여러
 * 애플리케이션 인스턴스에서 동시에 실행되더라도 실제 이벤트 분배는
 * {@link com.codeit.sb13.monew.activity.outbox.worker.claim.OutboxClaimService}가
 * 담당한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "monew.mongodb",
        name = {"enabled", "worker.enabled"},
        havingValue = "true"
)
public class OutboxWorkerScheduler {

    private final OutboxWorker outboxWorker;

    /**
     * Outbox를 한 번 처리하고 claim한 이벤트가 있을 때 실행 결과를 기록한다.
     */
    @Scheduled(fixedDelayString = "${monew.mongodb.worker.fixed-delay-ms:1000}")
    public void processOutbox() {
        OutboxWorkerResult result = outboxWorker.runOnce();
        if (result.selected() > 0) {
            log.info(
                    "Outbox worker 실행 완료. selected={}, processed={}, failed={}, unprocessed={}",
                    result.selected(),
                    result.processed(),
                    result.failed(),
                    result.unprocessed()
            );
        }
    }
}
