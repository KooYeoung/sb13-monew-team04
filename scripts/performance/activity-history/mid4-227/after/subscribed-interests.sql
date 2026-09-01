-- MID4-227 after: 구독 관심사와 관심사별 활성 구독자 수를 조회합니다.
-- SubscribeRepository.findSubscribedInterestActivities의 PostgreSQL 대응 SQL입니다.
SELECT
    s.id,
    s.created_at,
    i.id AS interest_id,
    i.created_at AS interest_created_at,
    i.name,
    i.updated_at AS interest_updated_at,
    (
        SELECT count(s2.id)
        FROM subscriptions s2
        WHERE s2.interest_id = i.id
          AND s2.visibility_status = 'ACTIVE'
    ) AS subscriber_count
FROM subscriptions s
JOIN interests i ON i.id = s.interest_id
WHERE s.user_id = '00000001-0000-4000-8000-000000000001'
  AND s.visibility_status = 'ACTIVE'
ORDER BY s.created_at DESC, s.id DESC;
