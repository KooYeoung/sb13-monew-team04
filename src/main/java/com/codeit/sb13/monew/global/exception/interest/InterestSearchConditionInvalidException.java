package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/**
 * 관심사 목록 조회 요청의 조회 조건({@code limit}, {@code cursor} 등)이 올바르지 않을 때
 * 던져지는 예외.
 *
 * <p>{@code IllegalArgumentException}을 직접 쓰지 않고 이 전용 타입을 두는 이유는, 전역
 * 예외 처리에서 {@code IllegalArgumentException}을 그대로 잡으면 이후 서비스나 저장소,
 * 또는 라이브러리 코드가 던지는 무관한 인자 오류까지 전부 "요청이 잘못됐다"는 400으로
 * 처리되기 때문이다. 요청 검증에서 비롯된 경우만 이 예외로 명시적으로 감싸, 전역 처리기가
 * 정확히 그 경우만 400으로 응답하도록 한다. 부가적으로, {@code IllegalArgumentException}은
 * Spring Data JPA 리포지토리 프록시를 거치면 {@code EntityManagerFactoryUtils}가
 * {@code InvalidDataAccessApiUsageException}으로 감싸버리는 문제가 있는데, 이 예외는
 * Spring이 인식하는 JPA 관련 예외 타입이 아니라서 그런 변환 없이 그대로 전달된다.</p>
 */
public class InterestSearchConditionInvalidException extends InterestException {

    public InterestSearchConditionInvalidException(String reason) {
        super(ApiErrorCode.INTEREST_SEARCH_CONDITION_INVALID, Map.of("reason", reason));
    }
}
