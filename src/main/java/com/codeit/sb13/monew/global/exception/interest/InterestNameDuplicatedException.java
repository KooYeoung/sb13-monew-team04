package com.codeit.sb13.monew.global.exception.interest;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

/**
 * 이미 존재하는 이름으로 관심사를 등록하려 할 때 던져지는 예외.
 *
 * <p>{@link com.codeit.sb13.monew.interest.service.InterestServiceImpl#create}에서
 * 두 시점에 발생할 수 있다. 저장 전에 {@code InterestRepository.existsByName}으로
 * 사전에 중복을 확인했을 때, 그리고 사전 확인을 통과했더라도 동시 요청으로 인해
 * 저장 시점에 {@code interests.name}의 유니크 제약을 위반했을 때다. 두 경우 모두
 * 이 시점의 {@code name}은 컨트롤러의 {@code @NotBlank} 검증을 이미 통과한
 * 뒤이므로 {@code null}일 수 없어, 다른 Interest 예외들과 달리 {@link Map#of}를
 * 그대로 사용해도 안전하다.</p>
 */
public class InterestNameDuplicatedException extends InterestException {

    /**
     * 중복된 관심사 이름을 받아 예외를 생성한다.
     *
     * @param name 이미 존재하는 것으로 확인된 관심사 이름
     */
    public InterestNameDuplicatedException(String name) {
        super(ApiErrorCode.INTEREST_NAME_DUPLICATED, Map.of("name", name));
    }

    public InterestNameDuplicatedException(String name, Throwable cause) {
        super(ApiErrorCode.INTEREST_NAME_DUPLICATED, Map.of("name", name));
        initCause(cause);
    }
}
