package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/**
 * Outbox worker 설정값이 안전한 polling 또는 claim lease 조건을 만족하지 않을 때 발생한다.
 */
public class OutboxWorkerConfigurationException extends OutboxException {

    /**
     * 잘못된 설정 항목과 값을 담아 예외를 생성한다.
     *
     * @param property 잘못된 설정 항목 이름
     * @param rejectedValue 거부된 설정값
     * @param reason 설정값을 사용할 수 없는 이유
     */
    public OutboxWorkerConfigurationException(
            String property,
            Object rejectedValue,
            String reason
    ) {
        super(ApiErrorCode.OUTBOX_WORKER_CONFIGURATION_INVALID, Map.of(
                "property", property,
                "rejectedValue", String.valueOf(rejectedValue),
                "reason", reason
        ));
    }
}
