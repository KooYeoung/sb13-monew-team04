package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/**
 * 재시도 한도를 소진한 이벤트에 다음 재시도 정책을 적용하려 할 때 발생한다.
 */
public class OutboxRetryPolicyException extends OutboxException {

    /**
     * 정책을 적용할 수 없는 retry count를 담아 예외를 생성한다.
     *
     * @param currentRetryCount 이번 시도 전까지 기록된 실패 횟수
     * @param maxRetryCount 허용된 최대 실패 횟수
     */
    public OutboxRetryPolicyException(int currentRetryCount, int maxRetryCount) {
        super(ApiErrorCode.OUTBOX_RETRY_POLICY_INVALID, Map.of(
                "currentRetryCount", currentRetryCount,
                "maxRetryCount", maxRetryCount
        ));
    }
}
