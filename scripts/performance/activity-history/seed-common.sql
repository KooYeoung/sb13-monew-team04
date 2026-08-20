-- MID4-130 활동내역 성능 테스트 seed입니다.
--
-- 로컬 PostgreSQL DB에 Flyway migration을 적용한 뒤 실행합니다.
--
-- .env.perf.local은 .env.example을 복사해서 만들고, MONEW_DB_PORT를 사용하지 않는 포트로 변경합니다.
-- Windows: Copy-Item .env.example .env.perf.local
-- macOS/Linux: cp .env.example .env.perf.local
-- 성능 seed는 -p monew-perf로 Compose project와 postgres-data 볼륨을 개발 환경과 분리합니다.
--
-- 실행 예시:
-- docker compose -p monew-perf --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=100k postgres-seed
-- docker compose -p monew-perf --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=1m postgres-seed
-- docker compose -p monew-perf --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=10m postgres-seed
--
-- 기준 사용자:
-- SELECT perf_uuid(1, 1) AS target_user_id;
--
-- 데이터 분포 확인:
-- SELECT min(date), max(date), count(DISTINCT date) FROM articles;
-- SELECT min(created_at), max(created_at), count(DISTINCT created_at) FROM comments;
-- SELECT min(created_at), max(created_at), count(DISTINCT created_at) FROM comment_likes;
-- SELECT min(viewed_at), max(viewed_at), count(DISTINCT viewed_at) FROM article_views;

CREATE OR REPLACE FUNCTION perf_uuid(namespace_code integer, seq bigint)
RETURNS uuid
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT (
        lpad(to_hex(namespace_code), 8, '0')
        || '-0000-4000-8000-'
        || lpad(to_hex(seq), 12, '0')
    )::uuid;
$$;

CREATE OR REPLACE FUNCTION seed_activity_history(scale_count integer)
RETURNS TABLE(metric text, value text)
LANGUAGE plpgsql
AS $$
DECLARE
    base_time timestamp := date_trunc('second', clock_timestamp());
    target_user_id uuid := perf_uuid(1, 1);
    target_rows integer;
    user_count integer;
    interest_count integer;
    article_count integer;
    comment_count integer;
    like_count integer;
    view_count integer;
    keyword_per_interest integer := 3;
    target_subscription_count integer := 50;
    subscription_per_user integer := 5;
    subscription_count integer;
BEGIN
    IF scale_count < 1000 THEN
        RAISE EXCEPTION 'scale_count must be at least 1000. requested=%', scale_count;
    END IF;

    target_rows := LEAST(10000, GREATEST(1000, scale_count / 100));
    user_count := LEAST(100000, GREATEST(1000, scale_count / 100));
    interest_count := LEAST(50000, GREATEST(200, scale_count / 200));
    article_count := GREATEST(target_rows * 2, scale_count / 5);
    comment_count := GREATEST(target_rows * 2, (scale_count * 4) / 10);
    like_count := GREATEST(target_rows, (scale_count * 3) / 10);
    view_count := GREATEST(target_rows, scale_count - comment_count - like_count);
    subscription_count := target_subscription_count + ((user_count - 1) * subscription_per_user);

    RAISE NOTICE 'Seeding activity history data. scale=%, users=%, interests=%, articles=%, comments=%, likes=%, views=%',
        scale_count, user_count, interest_count, article_count, comment_count, like_count, view_count;

    TRUNCATE TABLE
        notifications,
        comment_likes,
        comments,
        article_views,
        articles,
        subscriptions,
        keywords,
        interests,
        users
    RESTART IDENTITY CASCADE;

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
        perf_uuid(1, g),
        CASE
            WHEN g = 1 THEN 'activity-target@example.com'
            ELSE 'perf-user-' || g || '@example.com'
        END,
        CASE
            WHEN g = 1 THEN 'activity-target'
            ELSE 'perf-user-' || g
        END,
        'not-a-real-password-hash',
        base_time - ((g % 730) * interval '1 day') - (((g * 13) % 86400) * interval '1 second'),
        CASE
            WHEN g % 11 = 0 THEN base_time - ((g % 30) * interval '1 day')
            ELSE NULL
        END,
        CASE
            WHEN g <> 1 AND g % 100 = 0 THEN base_time - ((g % 90) * interval '1 day')
            ELSE NULL
        END
    FROM generate_series(1, user_count) AS g;

    INSERT INTO interests (
        id,
        name,
        created_at,
        updated_at
    )
    SELECT
        perf_uuid(2, g),
        'Interest ' || g,
        base_time - ((30 + (g % 365)) * interval '1 day') - (((g * 19) % 86400) * interval '1 second'),
        base_time - ((g % 30) * interval '1 day') - (((g * 7) % 86400) * interval '1 second')
    FROM generate_series(1, interest_count) AS g;

    INSERT INTO keywords (
        id,
        interest_id,
        keyword
    )
    SELECT
        perf_uuid(3, ((interest_seq - 1) * keyword_per_interest) + keyword_seq),
        perf_uuid(2, interest_seq),
        'keyword-' || interest_seq || '-' || keyword_seq
    FROM generate_series(1, interest_count) AS interest_seq
    CROSS JOIN generate_series(1, keyword_per_interest) AS keyword_seq;

    INSERT INTO subscriptions (
        id,
        interest_id,
        user_id
    )
    SELECT
        perf_uuid(4, g),
        perf_uuid(2, g),
        target_user_id
    FROM generate_series(1, target_subscription_count) AS g
    UNION ALL
    SELECT
        perf_uuid(4, target_subscription_count + ((user_seq - 2) * subscription_per_user) + slot),
        perf_uuid(2, ((user_seq + slot - 2) % interest_count) + 1),
        perf_uuid(1, user_seq)
    FROM generate_series(2, user_count) AS user_seq
    CROSS JOIN generate_series(1, subscription_per_user) AS slot;

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
    WITH rows AS (
        SELECT
            g,
            base_time
                - ((g % 365) * interval '1 day')
                - (((g * 17) % 86400) * interval '1 second') AS article_date
        FROM generate_series(1, article_count) AS g
    )
    SELECT
        perf_uuid(5, g),
        'Performance Article ' || g,
        'Performance seed summary for article ' || g,
        'https://example.com/performance/articles/' || g,
        article_date,
        CASE g % 4
            WHEN 0 THEN 'NAVER'
            WHEN 1 THEN 'HANKYUNG'
            WHEN 2 THEN 'CHOSUN'
            ELSE 'YEONHAP'
        END,
        article_date - interval '1 hour',
        article_date + interval '10 minutes',
        CASE
            WHEN g > target_rows AND g % 50 = 0 THEN article_date + interval '1 day'
            ELSE NULL
        END
    FROM rows;

    INSERT INTO comments (
        id,
        article_id,
        user_id,
        content,
        created_at,
        updated_at,
        deleted_at
    )
    WITH rows AS (
        SELECT
            g,
            CASE
                WHEN g <= target_rows THEN target_user_id
                ELSE perf_uuid(1, ((g - target_rows - 1) % (user_count - 1)) + 2)
            END AS comment_user_id,
            perf_uuid(5, ((g - 1) % article_count) + 1) AS comment_article_id,
            CASE
                WHEN g <= target_rows THEN base_time - (g * interval '1 second')
                ELSE base_time
                    - ((g % 180) * interval '1 day')
                    - (((g * 23) % 86400) * interval '1 second')
            END AS comment_created_at
        FROM generate_series(1, comment_count) AS g
    )
    SELECT
        perf_uuid(7, g),
        comment_article_id,
        comment_user_id,
        'Performance seed comment ' || g,
        comment_created_at,
        CASE
            WHEN g % 7 = 0 THEN comment_created_at + interval '2 hours'
            ELSE NULL
        END,
        CASE
            WHEN g > (target_rows * 2) AND g % 20 = 0 THEN comment_created_at + interval '1 day'
            ELSE NULL
        END
    FROM rows;

    INSERT INTO comment_likes (
        id,
        comment_id,
        liked_by,
        created_at
    )
    WITH rows AS (
        SELECT
            g,
            CASE
                WHEN g <= target_rows THEN perf_uuid(7, target_rows + g)
                ELSE perf_uuid(7, ((g - target_rows - 1) % comment_count) + 1)
            END AS liked_comment_id,
            CASE
                WHEN g <= target_rows THEN target_user_id
                ELSE perf_uuid(1, ((g - target_rows - 1) % (user_count - 1)) + 2)
            END AS like_user_id,
            CASE
                WHEN g <= target_rows THEN base_time - (g * interval '3 seconds')
                ELSE base_time
                    - ((g % 90) * interval '1 day')
                    - (((g * 29) % 86400) * interval '1 second')
            END AS like_created_at
        FROM generate_series(1, like_count) AS g
    )
    SELECT
        perf_uuid(8, g),
        liked_comment_id,
        like_user_id,
        like_created_at
    FROM rows;

    INSERT INTO article_views (
        id,
        article_id,
        user_id,
        viewed_at,
        created_at
    )
    WITH rows AS (
        SELECT
            g,
            CASE
                WHEN g <= target_rows THEN perf_uuid(5, g)
                ELSE perf_uuid(5, ((g - target_rows - 1) % article_count) + 1)
            END AS viewed_article_id,
            CASE
                WHEN g <= target_rows THEN target_user_id
                ELSE perf_uuid(1, (((g - target_rows - 1) / article_count) % (user_count - 1)) + 2)
            END AS viewer_user_id,
            CASE
                WHEN g <= target_rows THEN base_time - (g * interval '2 seconds')
                ELSE base_time
                    - ((g % 90) * interval '1 day')
                    - (((g * 31) % 86400) * interval '1 second')
            END AS article_viewed_at
        FROM generate_series(1, view_count) AS g
    )
    SELECT
        perf_uuid(6, g),
        viewed_article_id,
        viewer_user_id,
        article_viewed_at,
        article_viewed_at
    FROM rows;

    ANALYZE users;
    ANALYZE interests;
    ANALYZE keywords;
    ANALYZE subscriptions;
    ANALYZE articles;
    ANALYZE comments;
    ANALYZE comment_likes;
    ANALYZE article_views;

    RETURN QUERY
    SELECT *
    FROM (
        VALUES
            ('scale_count', scale_count::text),
            ('target_user_id', target_user_id::text),
            ('target_rows_per_recent_activity', target_rows::text),
            ('users', user_count::text),
            ('interests', interest_count::text),
            ('keywords', (interest_count * keyword_per_interest)::text),
            ('subscriptions', subscription_count::text),
            ('articles', article_count::text),
            ('comments', comment_count::text),
            ('comment_likes', like_count::text),
            ('article_views', view_count::text)
    ) AS result(metric, value);
END;
$$;
