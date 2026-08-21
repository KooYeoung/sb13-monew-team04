package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;

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

    /**
     * 조건에 맞는 관심사 목록을 커서 기반으로 조회한다.
     *
     * @param command 검색어, 정렬 기준, 커서, 페이지 크기, 요청자 id를 담은 커맨드
     * @return 조회된 관심사 목록과 다음 페이지를 위한 커서 정보
     */
    CursorPageResponseDto<InterestResponse> search(InterestSearchCommand command);
}
