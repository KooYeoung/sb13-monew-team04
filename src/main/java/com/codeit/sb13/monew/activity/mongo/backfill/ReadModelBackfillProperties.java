package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillConfigurationException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Read Model 초기 투영의 실행 식별자, polling과 claim lease를 제어한다.
 *
 * @param enabled 초기 투영 scheduler 활성화 여부
 * @param runId 실행을 식별하는 UUID. 완료 후 전체 재실행에는 새 UUID를 사용한다
 * @param fixedDelayMs batch 완료 후 다음 실행까지 기다릴 시간
 * @param batchSize 한 번에 처리할 원본 row 수
 * @param claimLease batch claim 유효 시간
 * @param heartbeatInterval claim 갱신 주기
 */
@ConfigurationProperties(prefix = "monew.mongodb.backfill")
public record ReadModelBackfillProperties(
        boolean enabled,
        UUID runId,
        long fixedDelayMs,
        int batchSize,
        Duration claimLease,
        Duration heartbeatInterval
) {
    public ReadModelBackfillProperties {
        if (enabled && runId == null) {
            throw invalid("runId", null, "활성화할 때 실행 UUID가 필요합니다.");
        }
        if (fixedDelayMs <= 0) {
            throw invalid("fixedDelayMs", fixedDelayMs, "0보다 커야 합니다.");
        }
        if (batchSize <= 0) {
            throw invalid("batchSize", batchSize, "0보다 커야 합니다.");
        }
        if (claimLease == null || claimLease.isZero() || claimLease.isNegative()) {
            throw invalid("claimLease", claimLease, "0보다 커야 합니다.");
        }
        if (heartbeatInterval == null
                || heartbeatInterval.isZero()
                || heartbeatInterval.isNegative()) {
            throw invalid("heartbeatInterval", heartbeatInterval, "0보다 커야 합니다.");
        }
        if (heartbeatInterval.compareTo(claimLease) >= 0) {
            throw invalid("heartbeatInterval", heartbeatInterval, "claimLease보다 짧아야 합니다.");
        }
    }

    private static ReadModelBackfillConfigurationException invalid(
            String property,
            Object value,
            String reason
    ) {
        return new ReadModelBackfillConfigurationException(property, value, reason);
    }
}
