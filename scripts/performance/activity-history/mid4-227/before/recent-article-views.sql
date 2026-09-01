-- MID4-227 before(44dcf82): 최근 조회한 비삭제 기사와 비삭제 사용자 기반 집계값을 조회합니다.
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
WHERE u.id = '00000001-0000-4000-8000-000000000001'
  AND a.deleted_at IS NULL
  AND u.deleted_at IS NULL
ORDER BY av.viewed_at DESC, av.id DESC
FETCH FIRST 10 ROWS ONLY;
