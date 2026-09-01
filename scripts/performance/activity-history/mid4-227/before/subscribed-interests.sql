-- MID4-227 before(44dcf82): 구독 관심사와 비삭제 사용자 기반 구독자 수를 조회합니다.
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
WHERE s.user_id = '00000001-0000-4000-8000-000000000001'
  AND u.deleted_at IS NULL
ORDER BY s.created_at DESC, s.id DESC;
