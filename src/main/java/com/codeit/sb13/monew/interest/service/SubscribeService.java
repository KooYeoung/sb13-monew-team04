package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.interest.controller.dto.SubscribeResponse;
import java.util.UUID;

public interface SubscribeService {

    /**
     * 사용자가 관심사를 구독한다.
     *
     * <p>이미 구독 중이면 새로 구독을 만들지 않고 기존 구독 정보를 그대로
     * 반환한다(멱등). 이 요청의 사용자 id는 헤더로 전달받을 뿐, 실제로 존재하는
     * 사용자인지는 이 계층에서 검증하지 않는다.</p>
     *
     * @param interestId 구독할 관심사 id
     * @param userId 구독하는 사용자 id
     * @return 구독 정보. 이미 구독 중이었다면 기존 구독의 정보를 그대로 담는다.
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException
     *         해당 id의 관심사가 존재하지 않는 경우
     */
    SubscribeResponse subscribe(UUID interestId, UUID userId);

    /**
     * 사용자가 관심사 구독을 취소한다.
     *
     * <p>구독하지 않은 관심사에 대한 취소 요청도 에러 없이 성공으로 처리한다(멱등).
     * 이 요청의 사용자 id는 헤더로 전달받을 뿐, 실제로 존재하는 사용자인지는 이
     * 계층에서 검증하지 않는다.</p>
     *
     * @param interestId 구독을 취소할 관심사 id
     * @param userId 구독을 취소하는 사용자 id
     * @throws com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException
     *         해당 id의 관심사가 존재하지 않는 경우
     */
    void unsubscribe(UUID interestId, UUID userId);
}
