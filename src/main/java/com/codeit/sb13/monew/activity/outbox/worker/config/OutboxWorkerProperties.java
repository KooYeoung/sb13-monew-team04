package com.codeit.sb13.monew.activity.outbox.worker.config;

import com.codeit.sb13.monew.global.exception.outbox.OutboxWorkerConfigurationException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox polling과 claim lease 동작을 제어하는 설정이다.
 *
 * @param enabled worker 활성화 여부
 * @param fixedDelayMs 한 polling 완료 후 다음 실행까지 기다릴 밀리초
 * @param batchSize 한 번에 claim할 최대 이벤트 수
 * @param claimLease heartbeat가 유지하는 claim 유효 시간
 * @param heartbeatInterval claim lease 갱신 주기이며 {@code claimLease}보다 짧아야 한다
 */
@ConfigurationProperties(prefix = "monew.mongodb.worker")
public record OutboxWorkerProperties(
        boolean enabled,
        long fixedDelayMs,
        int batchSize,
        Duration claimLease,
        Duration heartbeatInterval
) {
    public OutboxWorkerProperties {
        if (fixedDelayMs <= 0) {
            throw new OutboxWorkerConfigurationException(
                    "fixedDelayMs",
                    fixedDelayMs,
                    "0보다 커야 합니다."
            );
        }
        if (batchSize <= 0) {
            throw new OutboxWorkerConfigurationException(
                    "batchSize",
                    batchSize,
                    "0보다 커야 합니다."
            );
        }
        if (claimLease == null || claimLease.isZero() || claimLease.isNegative()) {
            throw new OutboxWorkerConfigurationException(
                    "claimLease",
                    claimLease,
                    "0보다 커야 합니다."
            );
        }
        if (heartbeatInterval == null
                || heartbeatInterval.isZero()
                || heartbeatInterval.isNegative()) {
            throw new OutboxWorkerConfigurationException(
                    "heartbeatInterval",
                    heartbeatInterval,
                    "0보다 커야 합니다."
            );
        }
        if (heartbeatInterval.compareTo(claimLease) >= 0) {
            throw new OutboxWorkerConfigurationException(
                    "heartbeatInterval",
                    heartbeatInterval,
                    "claimLease보다 짧아야 합니다."
            );
        }
    }
}
