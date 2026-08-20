-- RDB baseline 측정 전 특정 사용자별 최신 조회 쿼리만 선최적화되지 않도록 제거한다.
DROP INDEX IF EXISTS idx_article_views_user_viewed;
