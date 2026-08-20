package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;

public interface InterestService {

    /**
     * 새로운 관심사를 등록한다.
     *
     * @param command 등록할 관심사의 이름과 키워드 목록
     * @return 등록된 관심사 정보. 구독자 수는 0, 구독 여부는 false로 채워진다.
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException
     *         이미 존재하는 이름으로 등록을 시도한 경우
     */
    InterestResponse create(InterestCreateCommand command);
}
