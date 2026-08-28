-- MID4-206 활동내역 fan-out 극단 편중 reference overlay입니다.
--
-- 이 파일은 1차 fan-out 결과와 직접 비교하지 않는 별도 reference 조건입니다.
-- 대상 사용자가 구독 중인 관심사 1개에 구독자 50,000명을 추가해 subscriberCount 편중을 확인합니다.
--
-- 실행 전제:
-- - seed-common.sql과 seed-10m.sql을 먼저 적용해 10m seed와 perf_uuid 함수를 준비합니다.
-- - fanout-overlay.sql 적용 여부와 관계없이 실행할 수 있지만, 결과 용도는 항상 rdb-fanout-extreme-reference로 기록합니다.
--
-- 실행 예시:
-- docker exec -i <postgres-container> psql -U <user> -d <database> -v ON_ERROR_STOP=1 < scripts/performance/activity-history/fanout-extreme-overlay.sql

\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF to_regprocedure('perf_uuid(integer,bigint)') IS NULL THEN
        RAISE EXCEPTION 'perf_uuid(integer,bigint) 함수가 없습니다. seed-common.sql을 먼저 실행하세요.';
    END IF;
END $$;

CREATE TEMP TABLE fanout_extreme_clock ON COMMIT DROP AS
SELECT date_trunc('second', clock_timestamp()) AS base_time;

CREATE TEMP TABLE fanout_extreme_target_user ON COMMIT DROP AS
SELECT perf_uuid(1, 1) AS id;

-- 대상 사용자의 최근 구독 관심사 중 1개를 편중 대상으로 고정합니다.
CREATE TEMP TABLE fanout_extreme_interest ON COMMIT DROP AS
SELECT s.interest_id AS id
FROM subscriptions s
JOIN users u ON u.id = s.user_id
JOIN fanout_extreme_target_user target ON target.id = s.user_id
WHERE u.deleted_at IS NULL
ORDER BY s.created_at DESC, s.id DESC
LIMIT 1;

CREATE TEMP TABLE fanout_extreme_users ON COMMIT DROP AS
SELECT
    ordered.id AS user_id,
    row_number() OVER (ORDER BY ordered.id) AS rn
FROM (
    SELECT u.id
    FROM users u
    JOIN fanout_extreme_target_user target ON true
    CROSS JOIN fanout_extreme_interest interest
    WHERE u.deleted_at IS NULL
      AND u.id <> target.id
      AND NOT EXISTS (
          SELECT 1
          FROM subscriptions s
          WHERE s.interest_id = interest.id
            AND s.user_id = u.id
      )
    ORDER BY u.id
    LIMIT 50000
) ordered;

DO $$
DECLARE
    target_interest_count integer;
    user_count integer;
BEGIN
    SELECT count(*) INTO target_interest_count FROM fanout_extreme_interest;
    SELECT count(*) INTO user_count FROM fanout_extreme_users;

    IF target_interest_count <> 1 THEN
        RAISE EXCEPTION '편중 대상 관심사가 1개가 아닙니다. count=%', target_interest_count;
    END IF;

    IF user_count <> 50000 THEN
        RAISE EXCEPTION '편중 구독자 overlay 대상이 50,000명이 아닙니다. count=%', user_count;
    END IF;
END $$;

INSERT INTO subscriptions (
    id,
    interest_id,
    user_id,
    created_at
)
SELECT
    perf_uuid(95, eu.rn),
    interest.id,
    eu.user_id,
    ec.base_time - (eu.rn * interval '1 millisecond')
FROM fanout_extreme_users eu
CROSS JOIN fanout_extreme_interest interest
CROSS JOIN fanout_extreme_clock ec
ON CONFLICT DO NOTHING;

ANALYZE subscriptions;
ANALYZE users;
ANALYZE interests;

WITH extreme_count AS (
    SELECT count(s.id) AS active_subscriber_count
    FROM fanout_extreme_interest interest
    JOIN subscriptions s ON s.interest_id = interest.id
    JOIN users u ON u.id = s.user_id
    WHERE u.deleted_at IS NULL
)
SELECT 'fanout_extreme_target_interest' AS metric, id::text AS value
FROM fanout_extreme_interest
UNION ALL
SELECT 'fanout_extreme_subscription_overlay_rows', count(*)::text
FROM subscriptions
WHERE id IN (SELECT perf_uuid(95, g) FROM generate_series(1, 50000) AS g)
UNION ALL
SELECT 'fanout_extreme_active_subscriber_count', active_subscriber_count::text
FROM extreme_count;

COMMIT;
