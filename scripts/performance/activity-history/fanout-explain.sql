-- MID4-206 fan-out overlay 적용 후 활동내역 핵심 쿼리 실행 계획을 확인합니다.
--
-- 실행 전제:
-- - seed-common.sql, seed-10m.sql, fanout-overlay.sql을 같은 DB에 적용합니다.
-- - 극단 편중 reference를 보려면 fanout-extreme-overlay.sql 적용 후 같은 파일을 다시 실행합니다.
--
-- 실행 예시:
-- docker exec -i <postgres-container> psql -U <user> -d <database> -v ON_ERROR_STOP=1 < scripts/performance/activity-history/fanout-explain.sql

\set ON_ERROR_STOP on

-- 최근 작성 댓글: 댓글당 좋아요 수 subquery의 users PK 반복 조회를 확인합니다.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    c.id,
    a.id AS article_id,
    a.title AS article_title,
    u.id AS user_id,
    u.nickname AS user_nickname,
    c.content,
    (
        SELECT count(cl.id)
        FROM comment_likes cl
        JOIN users liked_user ON liked_user.id = cl.liked_by
        WHERE cl.comment_id = c.id
          AND liked_user.deleted_at IS NULL
    ) AS like_count,
    c.created_at
FROM comments c
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE u.id = perf_uuid(1, 1)
  AND u.deleted_at IS NULL
  AND c.deleted_at IS NULL
  AND a.deleted_at IS NULL
ORDER BY c.created_at DESC, c.id DESC
LIMIT 10;

-- 최근 좋아요 댓글: 좋아요한 댓글의 좋아요 수 subquery를 확인합니다.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    cl.id,
    cl.created_at,
    c.id AS comment_id,
    a.id AS article_id,
    a.title AS article_title,
    comment_user.id AS comment_user_id,
    comment_user.nickname AS comment_user_nickname,
    c.content AS comment_content,
    (
        SELECT count(cl2.id)
        FROM comment_likes cl2
        JOIN users liked_user ON liked_user.id = cl2.liked_by
        WHERE cl2.comment_id = c.id
          AND liked_user.deleted_at IS NULL
    ) AS comment_like_count,
    c.created_at AS comment_created_at
FROM comment_likes cl
JOIN comments c ON c.id = cl.comment_id
JOIN users liked_by ON liked_by.id = cl.liked_by
JOIN users comment_user ON comment_user.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE liked_by.id = perf_uuid(1, 1)
  AND liked_by.deleted_at IS NULL
  AND comment_user.deleted_at IS NULL
  AND a.deleted_at IS NULL
  AND c.deleted_at IS NULL
ORDER BY cl.created_at DESC, cl.id DESC
LIMIT 10;

-- 최근 조회 기사: 기사별 댓글 수와 조회 수 subquery를 확인합니다.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    av.id,
    u.id AS viewed_by,
    av.viewed_at,
    a.id AS article_id,
    a.source,
    a.link,
    a.title,
    a.date,
    a.summary,
    (
        SELECT count(c.id)
        FROM comments c
        JOIN users comment_user ON comment_user.id = c.user_id
        WHERE c.article_id = a.id
          AND c.deleted_at IS NULL
          AND comment_user.deleted_at IS NULL
    ) AS article_comment_count,
    (
        SELECT count(av2.id)
        FROM article_views av2
        JOIN users viewed_user ON viewed_user.id = av2.user_id
        WHERE av2.article_id = a.id
          AND viewed_user.deleted_at IS NULL
    ) AS article_view_count
FROM article_views av
JOIN articles a ON a.id = av.article_id
JOIN users u ON u.id = av.user_id
WHERE u.id = perf_uuid(1, 1)
  AND a.deleted_at IS NULL
  AND u.deleted_at IS NULL
ORDER BY av.viewed_at DESC, av.id DESC
LIMIT 10;

-- 구독 관심사: 관심사별 구독자 수 subquery의 users PK 반복 조회를 확인합니다.
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    s.id,
    s.created_at,
    i.id AS interest_id,
    i.name,
    i.created_at AS interest_created_at,
    i.updated_at AS interest_updated_at,
    (
        SELECT count(s2.id)
        FROM subscriptions s2
        JOIN users subscriber ON subscriber.id = s2.user_id
        JOIN interests i2 ON i2.id = s2.interest_id
        WHERE i2.id = i.id
          AND subscriber.deleted_at IS NULL
    ) AS interest_subscriber_count
FROM subscriptions s
JOIN interests i ON i.id = s.interest_id
JOIN users u ON u.id = s.user_id
WHERE s.user_id = perf_uuid(1, 1)
  AND u.deleted_at IS NULL
ORDER BY s.created_at DESC, s.id DESC;
