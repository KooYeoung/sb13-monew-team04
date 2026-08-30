package com.codeit.sb13.monew.activity.service;

import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ARTICLE_DELETED;

import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;

/**
 * 삭제 원인과 해당 원인이 활동 row에 반영할 노출 상태를 연결한다.
 *
 * <p>현재 MID4-222 범위에서는 기사 삭제 이벤트만 지원한다.
 * USER/COMMENT 삭제 이벤트는 각 도메인 작업에서 갱신 대상을 확정한 뒤 추가한다.</p>
 */
enum ActivityDeletionCause {
    // TODO: USER 삭제 작업 진행 시 USER_DELETED 매핑 추가 검토 필요.
    // TODO: COMMENT 삭제 작업 진행 시 COMMENT_DELETED 매핑 추가 검토 필요.
    ARTICLE(ARTICLE_DELETED);

    private final ActivityVisibilityStatus visibilityStatus;

    ActivityDeletionCause(ActivityVisibilityStatus visibilityStatus) {
        this.visibilityStatus = visibilityStatus;
    }

    /**
     * 삭제 원인에 대응하는 활동 row의 노출 상태를 반환한다.
     *
     * @return 삭제 원인별 노출 상태
     */
    ActivityVisibilityStatus visibilityStatus() {
        return visibilityStatus;
    }
}
