-- Article 테이블 수정: deleted_at 추가, UNIQUE 제약 추가
ALTER TABLE articles
    ADD COLUMN deleted_at timestamp null;

ALTER TABLE articles
    ADD CONSTRAINT uk_articles_link UNIQUE (link);

-- Article 인덱스
CREATE INDEX idx_articles_source ON articles(source);
CREATE INDEX idx_articles_date ON articles(date DESC);
CREATE INDEX idx_articles_source_date ON articles(source, date DESC);

-- ArticleView 제약 및 인덱스
ALTER TABLE article_views
    ADD CONSTRAINT uk_article_views_article_user UNIQUE (article_id, user_id);

CREATE INDEX idx_article_views_article_viewed ON article_views(article_id, viewed_at DESC);
CREATE INDEX idx_article_views_user_viewed ON article_views(user_id, viewed_at DESC);