-- Article: deleted_at 컬럼 추가 (소프트 삭제)
ALTER TABLE articles
    ADD COLUMN deleted_at timestamp null;

-- Article: link 컬럼 UNIQUE 제약 추가 (중복 기사 방지)
ALTER TABLE articles
    ADD CONSTRAINT uk_articles_link UNIQUE (link);

-- Article 인덱스: 출처별 조회
CREATE INDEX idx_articles_source ON articles(source);

-- Article 인덱스: 날짜별 최신순 조회
CREATE INDEX idx_articles_date ON articles(date DESC);

-- Article 인덱스: 출처 + 날짜 복합 (출처별 최신순 조회 성능)
CREATE INDEX idx_articles_source_date ON articles(source, date DESC);

-- ArticleView: created_at 컬럼 추가 (조회 기록 생성 시간)
ALTER TABLE article_views
    ADD COLUMN created_at timestamp not null default current_timestamp;

-- ArticleView: (article_id, user_id) 복합 UNIQUE 제약 추가 (중복 조회 방지)
ALTER TABLE article_views
    ADD CONSTRAINT uk_article_views_article_user UNIQUE (article_id, user_id);

-- ArticleView 인덱스: 기사별 조회 기록 (최신순)
CREATE INDEX idx_article_views_article_viewed ON article_views(article_id, viewed_at DESC);

-- ArticleView 인덱스: 사용자별 조회 기록 (최신순)
CREATE INDEX idx_article_views_user_viewed ON article_views(user_id, viewed_at DESC);