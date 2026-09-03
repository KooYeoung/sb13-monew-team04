package com.codeit.sb13.monew.activity.outbox.domain;

/**
 * worker가 역직렬화와 projection 처리 방식을 선택할 때 사용하는 이벤트 종류다.
 *
 * <p>값은 PostgreSQL {@code event_type}에 문자열로 저장되며,
 * {@link com.codeit.sb13.monew.activity.outbox.worker.OutboxEventDecoder}가 각 값에
 * 대응하는 payload record를 결정한다.</p>
 */
public enum OutboxEventType {
    INTEREST_SUBSCRIBED,
    INTEREST_UNSUBSCRIBED,
    COMMENT_WRITTEN,
    COMMENT_LIKED,
    COMMENT_LIKE_CANCELED,
    ARTICLE_VIEWED,
    INTEREST_UPDATED,
    INTEREST_HARD_DELETED,
    COMMENT_UPDATED,
    COMMENT_SOFT_DELETED,
    COMMENT_HARD_DELETED,
    ARTICLE_SOFT_DELETED,
    ARTICLE_HARD_DELETED,
    USER_NICKNAME_UPDATED,
    USER_SOFT_DELETED,
    USER_HARD_DELETED,
    INTEREST_SUBSCRIBER_COUNT_CHANGED,
    COMMENT_LIKE_CHANGED,
    ARTICLE_VIEW_COUNT_CHANGED,
    ARTICLE_COMMENT_COUNT_CHANGED;

    /**
     * polling batch 안에서 같은 snapshot 대상으로 병합할 수 있는 count 신호인지 확인한다.
     *
     * @return RDB 현재 집계값으로 수렴시키는 count 변경 이벤트이면 {@code true}
     */
    public boolean isCountChanged() {
        return switch (this) {
            case INTEREST_SUBSCRIBER_COUNT_CHANGED, COMMENT_LIKE_CHANGED,
                    ARTICLE_VIEW_COUNT_CHANGED, ARTICLE_COMMENT_COUNT_CHANGED -> true;
            default -> false;
        };
    }
}
