-- 동일한 댓글에 동일한 사용자가 중복 좋아요를 생성하지 않도록 복합 UNIQUE 제약 조건 추가
ALTER TABLE comment_likes
    ADD CONSTRAINT uk_comment_likes_comment_liked_by UNIQUE (comment_id, liked_by);
