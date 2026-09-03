package com.codeit.sb13.monew.global.exception.outbox;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.MonewException;
import java.util.Map;

/**
 * Outbox 저장과 worker 처리 과정에서 발생하는 애플리케이션 예외의 기반 타입이다.
 */
public abstract class OutboxException extends MonewException {

    /**
     * 원인 예외 없이 Outbox 예외를 생성한다.
     *
     * @param apiErrorCode 외부 오류 응답과 로그에 사용할 오류 코드
     * @param details 실패 문맥을 설명하는 상세 값
     */
    protected OutboxException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    /**
     * 원인 예외를 보존하는 Outbox 예외를 생성한다.
     *
     * @param apiErrorCode 외부 오류 응답과 로그에 사용할 오류 코드
     * @param details 실패 문맥을 설명하는 상세 값
     * @param cause 실제 실패 원인
     */
    protected OutboxException(
            ApiErrorCode apiErrorCode,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(apiErrorCode, details, cause);
    }
}
