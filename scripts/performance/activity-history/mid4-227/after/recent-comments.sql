-- MID4-227 after: 최근 작성 댓글과 댓글별 활성 좋아요 수를 조회합니다.
-- CommentRepository.findRecentCommentActivities의 PostgreSQL 대응 SQL입니다.
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
        WHERE cl.comment_id = c.id
          AND cl.visibility_status = 'ACTIVE'
    ) AS like_count,
    c.created_at
FROM comments c
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE u.id = '00000001-0000-4000-8000-000000000001'
  AND c.visibility_status = 'ACTIVE'
ORDER BY c.created_at DESC, c.id DESC
FETCH FIRST 10 ROWS ONLY;
