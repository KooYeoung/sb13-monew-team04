package com.codeit.sb13.monew.activity.service;

import java.util.UUID;

/** 사용자 활동내역의 네 조회 영역을 어떤 저장소에서 읽을지 추상화한다. */
public interface UserActivityReadSource {

    /**
     * 사용자의 최근 구독, 댓글, 댓글 좋아요, 기사 조회 내역을 읽는다.
     *
     * @param userId 조회할 사용자 ID
     * @return 외부 활동내역 DTO를 구성할 네 활동 목록
     */
    UserActivitySections read(UUID userId);
}
