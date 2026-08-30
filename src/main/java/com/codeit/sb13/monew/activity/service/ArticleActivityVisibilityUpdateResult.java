package com.codeit.sb13.monew.activity.service;

/**
 * 기사 삭제 이벤트 처리 후 활동 테이블별 노출 상태 갱신 건수를 담는다.
 *
 * @param articleViewCount 조회 기록 갱신 건수
 * @param commentCount 댓글 갱신 건수
 * @param commentLikeCount 댓글 좋아요 갱신 건수
 */
public record ArticleActivityVisibilityUpdateResult(
        long articleViewCount,
        long commentCount,
        long commentLikeCount
) {
}
