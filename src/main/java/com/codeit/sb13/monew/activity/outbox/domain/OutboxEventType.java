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
    ARTICLE_COMMENT_COUNT_CHANGED
}
