package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Collections;
import java.util.UUID;

/**
 * 관심사가 최소 1개의 키워드를 유지해야 한다는 불변조건을 위반하려 할 때
 * 던져지는 예외.
 *
 * <p>관심사에 남은 키워드가 1개뿐인 상태에서 그 키워드를 제거하려는
 * 시도가 있을 때 {@link com.codeit.sb13.monew.interest.domain.Interest#removeKeyword}
 * 에서 발생시킨다. 이 규칙은 영속 여부와 무관하게 항상 성립해야 하는
 * 순수한 도메인 규칙이므로, 아직 저장되지 않아 id가 없는 {@code Interest}
 * 에서도 예외 생성 자체는 실패하지 않아야 한다. 그래서 값이 {@code null}
 * 이어도 예외를 던지지 않는 {@link Collections#singletonMap}을 사용한다.</p>
 */
public class InterestKeywordRequiredException extends InterestException {

    /**
     * 대상 관심사의 id를 받아 예외를 생성한다.
     *
     * @param interestId 최소 키워드 조건을 위반하려 한 관심사의 id.
     *        아직 영속화되지 않은 관심사라면 {@code null}일 수 있다.
     */
    public InterestKeywordRequiredException(UUID interestId) {
        super(ApiErrorCode.INTEREST_KEYWORD_REQUIRED, Collections.singletonMap("interestId", interestId));
    }
}
