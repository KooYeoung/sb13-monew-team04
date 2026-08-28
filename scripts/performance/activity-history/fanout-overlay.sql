-- MID4-206 활동내역 fan-out 1차 overlay입니다.
--
-- 실행 전제:
-- - seed-common.sql과 seed-10m.sql을 먼저 적용해 10m seed와 perf_uuid 함수를 준비합니다.
-- - 대상 사용자는 seed 기본값인 00000001-0000-4000-8000-000000000001 입니다.
-- - 같은 파일을 다시 실행해도 overlay UUID와 unique 제약으로 중복 삽입되지 않습니다.
--
-- 실행 예시:
-- docker exec -i <postgres-container> psql -U <user> -d <database> -v ON_ERROR_STOP=1 < scripts/performance/activity-history/fanout-overlay.sql

\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF to_regprocedure('perf_uuid(integer,bigint)') IS NULL THEN
        RAISE EXCEPTION 'perf_uuid(integer,bigint) 함수가 없습니다. seed-common.sql을 먼저 실행하세요.';
    END IF;
END $$;

CREATE TEMP TABLE fanout_clock ON COMMIT DROP AS
SELECT date_trunc('second', clock_timestamp()) AS base_time;

CREATE TEMP TABLE fanout_target_user ON COMMIT DROP AS
SELECT perf_uuid(1, 1) AS id;

-- 최근 작성 댓글 10개를 고정합니다.
CREATE TEMP TABLE fanout_recent_comments ON COMMIT DROP AS
SELECT
    c.id,
    row_number() OVER (ORDER BY c.created_at DESC, c.id DESC) AS rn
FROM comments c
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
JOIN fanout_target_user target ON target.id = c.user_id
WHERE u.deleted_at IS NULL
  AND c.deleted_at IS NULL
  AND a.deleted_at IS NULL
ORDER BY c.created_at DESC, c.id DESC
LIMIT 10;

-- 최근 조회 기사 10개를 고정합니다.
CREATE TEMP TABLE fanout_recent_articles ON COMMIT DROP AS
SELECT
    av.article_id AS id,
    row_number() OVER (ORDER BY av.viewed_at DESC, av.id DESC) AS rn
FROM article_views av
JOIN users u ON u.id = av.user_id
JOIN articles a ON a.id = av.article_id
JOIN fanout_target_user target ON target.id = av.user_id
WHERE u.deleted_at IS NULL
  AND a.deleted_at IS NULL
ORDER BY av.viewed_at DESC, av.id DESC
LIMIT 10;

-- 대상 사용자가 구독 중인 관심사 50개를 고정합니다.
CREATE TEMP TABLE fanout_target_interests ON COMMIT DROP AS
SELECT
    s.interest_id AS id,
    row_number() OVER (ORDER BY s.created_at DESC, s.id DESC) AS rn
FROM subscriptions s
JOIN users u ON u.id = s.user_id
JOIN fanout_target_user target ON target.id = s.user_id
WHERE u.deleted_at IS NULL
ORDER BY s.created_at DESC, s.id DESC
LIMIT 50;

DO $$
DECLARE
    recent_comment_count integer;
    recent_article_count integer;
    target_interest_count integer;
BEGIN
    SELECT count(*) INTO recent_comment_count FROM fanout_recent_comments;
    SELECT count(*) INTO recent_article_count FROM fanout_recent_articles;
    SELECT count(*) INTO target_interest_count FROM fanout_target_interests;

    IF recent_comment_count <> 10 THEN
        RAISE EXCEPTION '최근 작성 댓글이 10개가 아닙니다. count=%', recent_comment_count;
    END IF;

    IF recent_article_count <> 10 THEN
        RAISE EXCEPTION '최근 조회 기사가 10개가 아닙니다. count=%', recent_article_count;
    END IF;

    IF target_interest_count <> 50 THEN
        RAISE EXCEPTION '구독 관심사가 50개가 아닙니다. count=%', target_interest_count;
    END IF;
END $$;

-- 최근 댓글 10개에 댓글당 좋아요 1,000개를 추가합니다.
CREATE TEMP TABLE fanout_comment_like_users ON COMMIT DROP AS
SELECT
    rc.id AS comment_id,
    rc.rn AS comment_rn,
    candidate.user_id,
    candidate.user_rn
FROM fanout_recent_comments rc
CROSS JOIN LATERAL (
    SELECT
        ordered.id AS user_id,
        row_number() OVER (ORDER BY ordered.id) AS user_rn
    FROM (
        SELECT u.id
        FROM users u
        JOIN fanout_target_user target ON true
        WHERE u.deleted_at IS NULL
          AND u.id <> target.id
          AND NOT EXISTS (
              SELECT 1
              FROM comment_likes cl
              WHERE cl.comment_id = rc.id
                AND cl.liked_by = u.id
          )
        ORDER BY u.id
        LIMIT 1000
    ) ordered
) candidate;

-- 최근 조회 기사 10개에 기사당 댓글 1,000개를 추가합니다.
CREATE TEMP TABLE fanout_article_comment_users ON COMMIT DROP AS
SELECT
    ra.id AS article_id,
    ra.rn AS article_rn,
    candidate.user_id,
    candidate.user_rn
FROM fanout_recent_articles ra
CROSS JOIN LATERAL (
    SELECT
        ordered.id AS user_id,
        row_number() OVER (ORDER BY ordered.id) AS user_rn
    FROM (
        SELECT u.id
        FROM users u
        JOIN fanout_target_user target ON true
        WHERE u.deleted_at IS NULL
          AND u.id <> target.id
        ORDER BY u.id
        LIMIT 1000
    ) ordered
) candidate;

-- 최근 조회 기사 10개에 기사당 조회 사용자 10,000명을 추가합니다.
CREATE TEMP TABLE fanout_article_view_users ON COMMIT DROP AS
SELECT
    ra.id AS article_id,
    ra.rn AS article_rn,
    candidate.user_id,
    candidate.user_rn
FROM fanout_recent_articles ra
CROSS JOIN LATERAL (
    SELECT
        ordered.id AS user_id,
        row_number() OVER (ORDER BY ordered.id) AS user_rn
    FROM (
        SELECT u.id
        FROM users u
        JOIN fanout_target_user target ON true
        WHERE u.deleted_at IS NULL
          AND u.id <> target.id
          AND NOT EXISTS (
              SELECT 1
              FROM article_views av
              WHERE av.article_id = ra.id
                AND av.user_id = u.id
          )
        ORDER BY u.id
        LIMIT 10000
    ) ordered
) candidate;

-- 구독 관심사 50개에 관심사당 구독자 1,000명을 추가합니다.
CREATE TEMP TABLE fanout_subscription_users ON COMMIT DROP AS
SELECT
    ti.id AS interest_id,
    ti.rn AS interest_rn,
    candidate.user_id,
    candidate.user_rn
FROM fanout_target_interests ti
CROSS JOIN LATERAL (
    SELECT
        ordered.id AS user_id,
        row_number() OVER (ORDER BY ordered.id) AS user_rn
    FROM (
        SELECT u.id
        FROM users u
        JOIN fanout_target_user target ON true
        WHERE u.deleted_at IS NULL
          AND u.id <> target.id
          AND NOT EXISTS (
              SELECT 1
              FROM subscriptions s
              WHERE s.interest_id = ti.id
                AND s.user_id = u.id
          )
        ORDER BY u.id
        LIMIT 1000
    ) ordered
) candidate;

DO $$
DECLARE
    comment_like_user_count integer;
    article_comment_user_count integer;
    article_view_user_count integer;
    subscription_user_count integer;
BEGIN
    SELECT count(*) INTO comment_like_user_count FROM fanout_comment_like_users;
    SELECT count(*) INTO article_comment_user_count FROM fanout_article_comment_users;
    SELECT count(*) INTO article_view_user_count FROM fanout_article_view_users;
    SELECT count(*) INTO subscription_user_count FROM fanout_subscription_users;

    IF comment_like_user_count <> 10000 THEN
        RAISE EXCEPTION '댓글 좋아요 overlay 대상이 10,000개가 아닙니다. count=%', comment_like_user_count;
    END IF;

    IF article_comment_user_count <> 10000 THEN
        RAISE EXCEPTION '기사 댓글 overlay 대상이 10,000개가 아닙니다. count=%', article_comment_user_count;
    END IF;

    IF article_view_user_count <> 100000 THEN
        RAISE EXCEPTION '기사 조회 overlay 대상이 100,000개가 아닙니다. count=%', article_view_user_count;
    END IF;

    IF subscription_user_count <> 50000 THEN
        RAISE EXCEPTION '구독 overlay 대상이 50,000개가 아닙니다. count=%', subscription_user_count;
    END IF;
END $$;

INSERT INTO comment_likes (
    id,
    comment_id,
    liked_by,
    created_at
)
SELECT
    perf_uuid(92, ((clu.comment_rn - 1) * 1000) + clu.user_rn),
    clu.comment_id,
    clu.user_id,
    fc.base_time - ((((clu.comment_rn - 1) * 1000) + clu.user_rn) * interval '1 millisecond')
FROM fanout_comment_like_users clu
CROSS JOIN fanout_clock fc
ON CONFLICT DO NOTHING;

INSERT INTO comments (
    id,
    article_id,
    user_id,
    content,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    perf_uuid(91, ((acu.article_rn - 1) * 1000) + acu.user_rn),
    acu.article_id,
    acu.user_id,
    'MID4-206 fan-out comment article-' || acu.article_rn || '-user-' || acu.user_rn,
    fc.base_time - ((((acu.article_rn - 1) * 1000) + acu.user_rn) * interval '1 millisecond'),
    NULL,
    NULL
FROM fanout_article_comment_users acu
CROSS JOIN fanout_clock fc
ON CONFLICT DO NOTHING;

INSERT INTO article_views (
    id,
    article_id,
    user_id,
    viewed_at,
    created_at
)
SELECT
    perf_uuid(93, ((avu.article_rn - 1) * 10000) + avu.user_rn),
    avu.article_id,
    avu.user_id,
    fc.base_time - ((((avu.article_rn - 1) * 10000) + avu.user_rn) * interval '1 millisecond'),
    fc.base_time - ((((avu.article_rn - 1) * 10000) + avu.user_rn) * interval '1 millisecond')
FROM fanout_article_view_users avu
CROSS JOIN fanout_clock fc
ON CONFLICT DO NOTHING;

INSERT INTO subscriptions (
    id,
    interest_id,
    user_id,
    created_at
)
SELECT
    perf_uuid(94, ((su.interest_rn - 1) * 1000) + su.user_rn),
    su.interest_id,
    su.user_id,
    fc.base_time - ((((su.interest_rn - 1) * 1000) + su.user_rn) * interval '1 millisecond')
FROM fanout_subscription_users su
CROSS JOIN fanout_clock fc
ON CONFLICT DO NOTHING;

ANALYZE users;
ANALYZE interests;
ANALYZE subscriptions;
ANALYZE articles;
ANALYZE comments;
ANALYZE comment_likes;
ANALYZE article_views;

-- overlay 적용 뒤 실제 카운트 범위를 확인합니다.
WITH recent_comment_counts AS (
    SELECT rc.id, count(cl.id) AS active_like_count
    FROM fanout_recent_comments rc
    LEFT JOIN comment_likes cl ON cl.comment_id = rc.id
    LEFT JOIN users u ON u.id = cl.liked_by
    WHERE u.id IS NULL OR u.deleted_at IS NULL
    GROUP BY rc.id
),
recent_article_comment_counts AS (
    SELECT ra.id, count(c.id) AS active_comment_count
    FROM fanout_recent_articles ra
    LEFT JOIN comments c ON c.article_id = ra.id
    LEFT JOIN users u ON u.id = c.user_id
    WHERE c.id IS NULL OR (c.deleted_at IS NULL AND u.deleted_at IS NULL)
    GROUP BY ra.id
),
recent_article_view_counts AS (
    SELECT ra.id, count(av.id) AS active_view_count
    FROM fanout_recent_articles ra
    LEFT JOIN article_views av ON av.article_id = ra.id
    LEFT JOIN users u ON u.id = av.user_id
    WHERE u.id IS NULL OR u.deleted_at IS NULL
    GROUP BY ra.id
),
target_interest_counts AS (
    SELECT ti.id, count(s.id) AS active_subscriber_count
    FROM fanout_target_interests ti
    LEFT JOIN subscriptions s ON s.interest_id = ti.id
    LEFT JOIN users u ON u.id = s.user_id
    WHERE u.id IS NULL OR u.deleted_at IS NULL
    GROUP BY ti.id
)
SELECT 'fanout_recent_comments' AS metric, count(*)::text AS value FROM fanout_recent_comments
UNION ALL
SELECT 'fanout_recent_articles', count(*)::text FROM fanout_recent_articles
UNION ALL
SELECT 'fanout_target_interests', count(*)::text FROM fanout_target_interests
UNION ALL
SELECT 'fanout_comment_likes_overlay_rows', count(*)::text
FROM comment_likes
WHERE id IN (SELECT perf_uuid(92, g) FROM generate_series(1, 10000) AS g)
UNION ALL
SELECT 'fanout_article_comments_overlay_rows', count(*)::text
FROM comments
WHERE id IN (SELECT perf_uuid(91, g) FROM generate_series(1, 10000) AS g)
UNION ALL
SELECT 'fanout_article_views_overlay_rows', count(*)::text
FROM article_views
WHERE id IN (SELECT perf_uuid(93, g) FROM generate_series(1, 100000) AS g)
UNION ALL
SELECT 'fanout_subscriptions_overlay_rows', count(*)::text
FROM subscriptions
WHERE id IN (SELECT perf_uuid(94, g) FROM generate_series(1, 50000) AS g)
UNION ALL
SELECT 'recent_comment_active_like_count_min', min(active_like_count)::text FROM recent_comment_counts
UNION ALL
SELECT 'recent_comment_active_like_count_max', max(active_like_count)::text FROM recent_comment_counts
UNION ALL
SELECT 'recent_article_active_comment_count_min', min(active_comment_count)::text FROM recent_article_comment_counts
UNION ALL
SELECT 'recent_article_active_comment_count_max', max(active_comment_count)::text FROM recent_article_comment_counts
UNION ALL
SELECT 'recent_article_active_view_count_min', min(active_view_count)::text FROM recent_article_view_counts
UNION ALL
SELECT 'recent_article_active_view_count_max', max(active_view_count)::text FROM recent_article_view_counts
UNION ALL
SELECT 'target_interest_active_subscriber_count_min', min(active_subscriber_count)::text FROM target_interest_counts
UNION ALL
SELECT 'target_interest_active_subscriber_count_max', max(active_subscriber_count)::text FROM target_interest_counts;

COMMIT;
