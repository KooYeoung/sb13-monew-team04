-- MID4-227 before(44dcf82): 최근 좋아요한 비삭제 댓글과 좋아요 수를 조회합니다.
SELECT
    cl.id,
    cl.created_at,
    c.id AS comment_id,
    a.id AS article_id,
    a.title AS article_title,
    comment_user.id AS comment_user_id,
    comment_user.nickname AS comment_user_nickname,
    c.content AS comment_content,
    (
        SELECT count(cl2.id)
        FROM comment_likes cl2
        JOIN comments c2 ON c2.id = cl2.comment_id
        JOIN users liked_user ON liked_user.id = cl2.liked_by
        WHERE c2.id = c.id
          AND liked_user.deleted_at IS NULL
    ) AS comment_like_count,
    c.created_at AS comment_created_at
FROM comment_likes cl
JOIN comments c ON c.id = cl.comment_id
JOIN users liked_by ON liked_by.id = cl.liked_by
JOIN users comment_user ON comment_user.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE liked_by.id = '00000001-0000-4000-8000-000000000001'
  AND liked_by.deleted_at IS NULL
  AND comment_user.deleted_at IS NULL
  AND a.deleted_at IS NULL
  AND c.deleted_at IS NULL
ORDER BY cl.created_at DESC, cl.id DESC
FETCH FIRST 10 ROWS ONLY;
