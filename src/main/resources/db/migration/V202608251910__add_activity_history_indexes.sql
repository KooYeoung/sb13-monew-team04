-- MID4-133: MID4-132 RDB baseline에서 선정한 활동내역 조회 경로 인덱스.

-- 최근 작성 댓글: 사용자 조건과 최신순 정렬.
CREATE INDEX idx_comments_user_created_id
    ON comments(user_id, created_at DESC, id DESC);

-- 최근 조회 기사: 기사별 댓글 수 subquery.
CREATE INDEX idx_comments_article
    ON comments(article_id);

-- 최근 좋아요한 댓글: 좋아요한 사용자 조건과 최신순 정렬.
CREATE INDEX idx_comment_likes_liked_by_created_id
    ON comment_likes(liked_by, created_at DESC, id DESC);

-- 최근 조회 기사: 사용자 조건과 최신순 정렬.
-- 기존 idx_article_views_user_viewed는 baseline 측정을 위해 제거된 인덱스다.
-- 최종 활동내역 조회의 정렬 조건에 맞추기 위해 id DESC를 포함한다.
CREATE INDEX idx_article_views_user_viewed_id
    ON article_views(user_id, viewed_at DESC, id DESC);

-- 구독 중인 관심사: 사용자 조건과 최신순 정렬.
CREATE INDEX idx_subscriptions_user_created_id
    ON subscriptions(user_id, created_at DESC, id DESC);
