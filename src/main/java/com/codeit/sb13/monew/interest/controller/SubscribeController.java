package com.codeit.sb13.monew.interest.controller;

import com.codeit.sb13.monew.global.MonewHttpHeaders;
import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.interest.controller.dto.SubscribeResponse;
import com.codeit.sb13.monew.interest.service.SubscribeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관심사 구독 API.
 *
 * <p>구독은 관심사에 종속된 하위 자원이라 {@code /api/interests/{interestId}/subscriptions}
 * 경로를 쓰지만, 구독 자체의 생성 로직은 관심사 CRUD와 책임이 달라
 * {@link InterestController}가 아니라 별도 컨트롤러로 분리했다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interests/{interestId}/subscriptions")
public class SubscribeController {

    private final SubscribeService subscribeService;

    /**
     * 관심사를 구독한다.
     *
     * <p>이미 구독 중인 관심사를 다시 구독 요청해도 에러 없이 기존 구독 정보를
     * 그대로 응답한다(멱등). 존재하지 않는 {@code interestId}면
     * {@link InterestNotFoundException}이 발생해 {@code INT_001}(404)로 응답한다.</p>
     *
     * @param interestId 구독할 관심사 id
     * @param requestUserId 구독하는 사용자 id
     * @return 200 상태코드와 구독 정보
     */
    @PostMapping
    public ResponseEntity<SubscribeResponse> subscribe(
            @PathVariable UUID interestId,
            @RequestHeader(MonewHttpHeaders.REQUEST_USER_ID) UUID requestUserId
    ) {
        SubscribeResponse response = subscribeService.subscribe(interestId, requestUserId);
        return ResponseEntity.ok(response);
    }
}
