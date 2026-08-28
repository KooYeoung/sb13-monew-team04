-- MID4-206 제외 조건 필터 비용 측정용 overlay입니다.
--
-- 실행 전제:
-- - seed-common.sql, seed-10m.sql을 같은 DB에 먼저 적용합니다.
-- - fanout-overlay.sql과 동시에 적용하지 않습니다.
--
-- 목적:
-- - 최신순 인덱스가 읽는 후보 row 중 soft-delete 조건으로 제외되는 데이터를 늘립니다.
-- - 물리 삭제로 사라지는 좋아요 취소/구독 해제 row는 RDB에 남지 않으므로, 현재 스키마에서 관찰 가능한 삭제 사용자/삭제 기사/삭제 댓글 조건에 집중합니다.

\set ON_ERROR_STOP on

DO $$
DECLARE
    base_time timestamp := date_trunc('second', clock_timestamp());
    target_user_id uuid := perf_uuid(1, 1);
    invalid_recent_comment_count integer := 10000;
    invalid_liked_comment_count integer := 10000;
    invalid_viewed_article_count integer := 10000;
    deleted_subscriber_per_interest integer := 1000;
BEGIN
    RAISE NOTICE 'Applying exclusion overlay. target_user_id=%', target_user_id;

    -- 최근 작성 댓글 조회가 최신순 인덱스를 타고 들어간 뒤 삭제 댓글을 많이 지나가도록 합니다.
    INSERT INTO comments (
        id,
        article_id,
        user_id,
        content,
        created_at,
        updated_at,
        deleted_at,
        visibility_status
    )
    SELECT
        perf_uuid(96, g),
        perf_uuid(5, ((g - 1) % 10000) + 1),
        target_user_id,
        'MID4-206 exclusion deleted recent comment ' || g,
        base_time + interval '1 hour' - (g * interval '1 millisecond'),
        NULL,
        base_time + interval '1 hour' - (g * interval '1 millisecond') + interval '1 second',
        'COMMENT_DELETED'
    FROM generate_series(1, invalid_recent_comment_count) AS g
    ON CONFLICT (id) DO NOTHING;

    -- 최근 좋아요 댓글 조회가 삭제 댓글을 많이 지나가도록 target user의 좋아요 row를 추가합니다.
    INSERT INTO comments (
        id,
        article_id,
        user_id,
        content,
        created_at,
        updated_at,
        deleted_at,
        visibility_status
    )
    SELECT
        perf_uuid(97, g),
        perf_uuid(5, ((g - 1) % 10000) + 1),
        perf_uuid(1, 1001 + ((g - 1) % 500)),
        'MID4-206 exclusion deleted liked comment ' || g,
        base_time + interval '2 hours' - (g * interval '1 millisecond'),
        NULL,
        base_time + interval '2 hours' - (g * interval '1 millisecond') + interval '1 second',
        'COMMENT_DELETED'
    FROM generate_series(1, invalid_liked_comment_count) AS g
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO comment_likes (
        id,
        comment_id,
        liked_by,
        created_at,
        visibility_status
    )
    SELECT
        perf_uuid(98, g),
        perf_uuid(97, g),
        target_user_id,
        base_time + interval '2 hours' - (g * interval '1 millisecond'),
        'COMMENT_DELETED'
    FROM generate_series(1, invalid_liked_comment_count) AS g
    ON CONFLICT (id) DO NOTHING;

    -- 최근 조회 기사 조회가 삭제 기사를 많이 지나가도록 target user의 조회 row를 추가합니다.
    INSERT INTO articles (
        id,
        title,
        summary,
        link,
        date,
        source,
        created_at,
        updated_at,
        deleted_at
    )
    SELECT
        perf_uuid(99, g),
        'MID4-206 Exclusion Deleted Article ' || g,
        'MID4-206 exclusion summary for deleted article ' || g,
        'https://example.com/performance/exclusion/articles/' || g,
        base_time + interval '3 hours' - (g * interval '1 millisecond'),
        CASE g % 4
            WHEN 0 THEN 'NAVER'
            WHEN 1 THEN 'HANKYUNG'
            WHEN 2 THEN 'CHOSUN'
            ELSE 'YEONHAP'
        END,
        base_time + interval '3 hours' - (g * interval '1 millisecond'),
        base_time + interval '3 hours' - (g * interval '1 millisecond'),
        base_time + interval '3 hours' - (g * interval '1 millisecond') + interval '1 second'
    FROM generate_series(1, invalid_viewed_article_count) AS g
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO article_views (
        id,
        article_id,
        user_id,
        viewed_at,
        created_at,
        visibility_status
    )
    SELECT
        perf_uuid(100, g),
        perf_uuid(99, g),
        target_user_id,
        base_time + interval '3 hours' - (g * interval '1 millisecond'),
        base_time + interval '3 hours' - (g * interval '1 millisecond'),
        'ARTICLE_DELETED'
    FROM generate_series(1, invalid_viewed_article_count) AS g
    ON CONFLICT (id) DO NOTHING;

    CREATE TEMP TABLE tmp_mid4_206_exclusion_target_interests ON COMMIT DROP AS
    SELECT
        s.interest_id,
        row_number() OVER (ORDER BY s.created_at DESC, s.id DESC) AS interest_rn
    FROM subscriptions s
    WHERE s.user_id = target_user_id
    ORDER BY s.created_at DESC, s.id DESC
    LIMIT 50;

    -- 구독 관심사 subscriberCount subquery가 삭제 사용자를 많이 확인하도록 관계 row를 추가합니다.
    INSERT INTO users (
        id,
        email,
        nickname,
        password,
        created_at,
        updated_at,
        deleted_at
    )
    SELECT
        perf_uuid(101, generated.seq),
        'mid4-206-deleted-subscriber-' || generated.seq || '@example.com',
        'mid4-206-deleted-subscriber-' || generated.seq,
        'not-a-real-password-hash',
        base_time - interval '30 days',
        NULL,
        base_time - interval '1 day'
    FROM (
        SELECT
            ((interest_rn - 1) * deleted_subscriber_per_interest) + subscriber_seq AS seq
        FROM tmp_mid4_206_exclusion_target_interests
        CROSS JOIN generate_series(1, deleted_subscriber_per_interest) AS subscriber_seq
    ) AS generated
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO subscriptions (
        id,
        interest_id,
        user_id,
        created_at,
        visibility_status
    )
    SELECT
        perf_uuid(102, generated.seq),
        generated.interest_id,
        perf_uuid(101, generated.seq),
        base_time - interval '1 day' - (generated.seq * interval '1 millisecond'),
        'USER_DELETED'
    FROM (
        SELECT
            interest_id,
            ((interest_rn - 1) * deleted_subscriber_per_interest) + subscriber_seq AS seq
        FROM tmp_mid4_206_exclusion_target_interests
        CROSS JOIN generate_series(1, deleted_subscriber_per_interest) AS subscriber_seq
    ) AS generated
    ON CONFLICT (id) DO NOTHING;

    ANALYZE users;
    ANALYZE subscriptions;
    ANALYZE articles;
    ANALYZE comments;
    ANALYZE comment_likes;
    ANALYZE article_views;
END;
$$;

SELECT 'invalid_recent_deleted_comments_for_target' AS metric, count(*)::text AS value
FROM comments
WHERE user_id = perf_uuid(1, 1)
  AND id >= perf_uuid(96, 1)
  AND id <= perf_uuid(96, 10000)
  AND deleted_at IS NOT NULL
UNION ALL
SELECT 'invalid_liked_deleted_comments_for_target', count(*)::text
FROM comment_likes cl
JOIN comments c ON c.id = cl.comment_id
WHERE cl.liked_by = perf_uuid(1, 1)
  AND cl.id >= perf_uuid(98, 1)
  AND cl.id <= perf_uuid(98, 10000)
  AND c.deleted_at IS NOT NULL
UNION ALL
SELECT 'invalid_viewed_deleted_articles_for_target', count(*)::text
FROM article_views av
JOIN articles a ON a.id = av.article_id
WHERE av.user_id = perf_uuid(1, 1)
  AND av.id >= perf_uuid(100, 1)
  AND av.id <= perf_uuid(100, 10000)
  AND a.deleted_at IS NOT NULL
UNION ALL
SELECT 'deleted_subscribers_added_to_target_interests', count(*)::text
FROM subscriptions s
JOIN users u ON u.id = s.user_id
WHERE s.id >= perf_uuid(102, 1)
  AND s.id <= perf_uuid(102, 50000)
  AND u.deleted_at IS NOT NULL;
