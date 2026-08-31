package com.codeit.sb13.monew.activity.service;

import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import java.util.UUID;

/**
 * 활동 노출 상태 갱신 대상이 되는 삭제 이벤트를 표현한다.
 *
 * @param cause 삭제 원인
 * @param targetId 삭제된 도메인 id
 */
record ActivityDeletionTarget(
        ActivityDeletionCause cause,
        UUID targetId
) {

    /**
     * 기사 삭제 이벤트를 활동 노출 상태 갱신 대상으로 만든다.
     *
     * @param articleId 삭제된 기사 id
     * @return 기사 삭제 대상
     */
    static ActivityDeletionTarget deletedArticle(UUID articleId) {
        return new ActivityDeletionTarget(ActivityDeletionCause.ARTICLE, articleId);
    }

    static ActivityDeletionTarget deletedUser(UUID userId) {
        return new ActivityDeletionTarget(ActivityDeletionCause.USER, userId);
    }

    // TODO: COMMENT 삭제 작업 진행 시 대상 factory 추가 여부 검토 필요.

    /**
     * 삭제 원인에 대응하는 저장 상태를 반환한다.
     *
     * @return 활동 row에 반영할 노출 상태
     */
    ActivityVisibilityStatus targetStatus() {
        return cause.visibilityStatus();
    }
}
