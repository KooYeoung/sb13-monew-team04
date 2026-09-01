-- MID4-227 after: 최근 조회 기사와 기사별 활성 댓글·조회 수를 조회합니다.
-- ArticleViewRepository.findRecentArticleViewActivities의 PostgreSQL 대응 SQL입니다.
SELECT
    av.id,
    av.user_id AS viewed_by,
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
        WHERE c.article_id = a.id
          AND c.visibility_status = 'ACTIVE'
    ) AS article_comment_count,
    (
        SELECT count(av2.id)
        FROM article_views av2
        WHERE av2.article_id = a.id
          AND av2.visibility_status = 'ACTIVE'
    ) AS article_view_count
FROM article_views av
JOIN articles a ON a.id = av.article_id
WHERE av.user_id = '00000001-0000-4000-8000-000000000001'
  AND av.visibility_status = 'ACTIVE'
ORDER BY av.viewed_at DESC, av.id DESC
FETCH FIRST 10 ROWS ONLY;
