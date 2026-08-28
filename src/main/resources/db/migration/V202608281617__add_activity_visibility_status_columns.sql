ALTER TABLE subscriptions
    ADD COLUMN visibility_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE article_views
    ADD COLUMN visibility_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE comments
    ADD COLUMN visibility_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE comment_likes
    ADD COLUMN visibility_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

-- 기존 데이터는 기본값 ACTIVE로 채운 뒤 현재 논리 삭제 상태를 조회용 노출 상태로 보정한다.
-- 여러 삭제 조건이 겹치는 경우 더 가까운 도메인 삭제 사유를 먼저 반영하고, 사용자 삭제 사유는 마지막에만 반영한다.
-- 각 UPDATE는 visibility_status = 'ACTIVE' 조건으로 이미 반영된 더 구체적인 삭제 사유를 덮어쓰지 않는다.

-- 구독은 대상 사용자 삭제 여부만 조회 노출 상태에 반영한다.
UPDATE subscriptions s
SET visibility_status = 'USER_DELETED'
FROM users u
WHERE s.user_id = u.id
  AND u.deleted_at IS NOT NULL
  AND s.visibility_status = 'ACTIVE';

-- 기사 조회는 조회 대상 기사 삭제를 먼저 반영한다.
UPDATE article_views av
SET visibility_status = 'ARTICLE_DELETED'
FROM articles a
WHERE av.article_id = a.id
  AND a.deleted_at IS NOT NULL
  AND av.visibility_status = 'ACTIVE';

-- 기사 조회 사용자가 삭제된 경우는 기사 삭제보다 후순위로 반영한다.
UPDATE article_views av
SET visibility_status = 'USER_DELETED'
FROM users u
WHERE av.user_id = u.id
  AND u.deleted_at IS NOT NULL
  AND av.visibility_status = 'ACTIVE';

-- 댓글 자체가 삭제된 경우를 가장 먼저 반영한다.
UPDATE comments c
SET visibility_status = 'COMMENT_DELETED'
WHERE c.deleted_at IS NOT NULL
  AND c.visibility_status = 'ACTIVE';

-- 댓글이 속한 기사가 삭제된 경우는 댓글 자체 삭제보다 후순위로 반영한다.
UPDATE comments c
SET visibility_status = 'ARTICLE_DELETED'
FROM articles a
WHERE c.article_id = a.id
  AND a.deleted_at IS NOT NULL
  AND c.visibility_status = 'ACTIVE';

-- 댓글 작성자가 삭제된 경우는 댓글/기사 삭제보다 후순위로 반영한다.
UPDATE comments c
SET visibility_status = 'USER_DELETED'
FROM users u
WHERE c.user_id = u.id
  AND u.deleted_at IS NOT NULL
  AND c.visibility_status = 'ACTIVE';

-- 댓글 좋아요는 좋아요 대상 댓글 삭제를 먼저 반영한다.
UPDATE comment_likes cl
SET visibility_status = 'COMMENT_DELETED'
FROM comments c
WHERE cl.comment_id = c.id
  AND c.deleted_at IS NOT NULL
  AND cl.visibility_status = 'ACTIVE';

-- 댓글 좋아요가 속한 기사 삭제는 댓글 삭제보다 후순위, 사용자 삭제보다 우선으로 반영한다.
UPDATE comment_likes cl
SET visibility_status = 'ARTICLE_DELETED'
FROM comments c
JOIN articles a ON c.article_id = a.id
WHERE cl.comment_id = c.id
  AND a.deleted_at IS NOT NULL
  AND cl.visibility_status = 'ACTIVE';

-- 좋아요를 누른 사용자가 삭제된 경우는 댓글/기사 삭제보다 후순위로 반영한다.
UPDATE comment_likes cl
SET visibility_status = 'USER_DELETED'
FROM users liked_by_user
WHERE cl.liked_by = liked_by_user.id
  AND liked_by_user.deleted_at IS NOT NULL
  AND cl.visibility_status = 'ACTIVE';

-- 좋아요 대상 댓글의 작성자가 삭제된 경우도 댓글/기사 삭제보다 후순위로 반영한다.
UPDATE comment_likes cl
SET visibility_status = 'USER_DELETED'
FROM comments c
JOIN users comment_user ON c.user_id = comment_user.id
WHERE cl.comment_id = c.id
  AND comment_user.deleted_at IS NOT NULL
  AND cl.visibility_status = 'ACTIVE';
