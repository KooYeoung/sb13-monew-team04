-- MID4-227 after: 최근 좋아요한 댓글과 댓글별 활성 좋아요 수를 조회합니다.
-- CommentLikeRepository.findRecentCommentLikeActivity의 PostgreSQL 대응 SQL입니다.
SELECT
    cl.id,
    cl.created_at,
    c.id AS comment_id,
    a.id AS article_id,
    a.title AS article_title,
    u.id AS comment_user_id,
    u.nickname AS comment_user_nickname,
    c.content AS comment_content,
    (
        SELECT count(cl2.id)
        FROM comment_likes cl2
        WHERE cl2.comment_id = c.id
          AND cl2.visibility_status = 'ACTIVE'
    ) AS comment_like_count,
    c.created_at AS comment_created_at
FROM comment_likes cl
JOIN comments c ON c.id = cl.comment_id
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE cl.liked_by = '00000001-0000-4000-8000-000000000001'
  AND cl.visibility_status = 'ACTIVE'
ORDER BY cl.created_at DESC, cl.id DESC
FETCH FIRST 10 ROWS ONLY;
