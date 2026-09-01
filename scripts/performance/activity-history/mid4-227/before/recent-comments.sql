-- MID4-227 before(44dcf82): 최근 작성 댓글과 댓글별 비삭제 사용자 좋아요 수를 조회합니다.
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
        JOIN users liked_user ON liked_user.id = cl.liked_by
        WHERE cl.comment_id = c.id
          AND liked_user.deleted_at IS NULL
    ) AS like_count,
    c.created_at
FROM comments c
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE u.id = '00000001-0000-4000-8000-000000000001'
  AND u.deleted_at IS NULL
  AND c.deleted_at IS NULL
  AND a.deleted_at IS NULL
ORDER BY c.created_at DESC, c.id DESC
FETCH FIRST 10 ROWS ONLY;
