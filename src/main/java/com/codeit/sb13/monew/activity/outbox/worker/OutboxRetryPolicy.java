package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.global.exception.outbox.OutboxRetryPolicyException;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Outbox 처리 실패 횟수에 따른 재시도 간격과 Dead Letter 기준을 제공한다.
 *
 * <p>1·5·15·60분 간격으로 네 번 재시도하고 다섯 번째 실패를 자동 처리의
 * 종료 시점으로 판단한다.</p>
 */
@Component
public class OutboxRetryPolicy {

    private static final int MAX_RETRY_COUNT = 5;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1)
    );

    /**
     * 이번 실패를 기록하면 최대 실패 횟수에 도달하는지 확인한다.
     *
     * @param currentRetryCount 이번 시도 전까지 저장된 실패 횟수
     * @return 이번 실패가 Dead Letter 전환 대상이면 {@code true}
     */
    public boolean exhaustedAfter(int currentRetryCount) {
        return currentRetryCount + 1 >= MAX_RETRY_COUNT;
    }

    /**
     * 이번 실패 이후 적용할 재시도 대기 시간을 반환한다.
     *
     * @param currentRetryCount 이번 시도 전까지 저장된 실패 횟수
     * @return 다음 처리까지 기다릴 시간
     * @throws OutboxRetryPolicyException 이번 실패로 최대 횟수를 소진하는 경우
     */
    public Duration delayAfter(int currentRetryCount) {
        if (exhaustedAfter(currentRetryCount)) {
            throw new OutboxRetryPolicyException(currentRetryCount, MAX_RETRY_COUNT);
        }
        return RETRY_DELAYS.get(currentRetryCount);
    }
}
