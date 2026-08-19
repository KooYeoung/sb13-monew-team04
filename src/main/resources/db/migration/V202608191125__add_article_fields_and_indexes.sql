-- Article 테이블 수정: view_count, comment_count, deleted_at 추가
alter table articles
    add column view_count integer not null default 0,
    add column comment_count integer not null default 0,
    add column deleted_at timestamp null;

-- Article 테이블 UNIQUE 제약 추가
alter table articles
    add constraint uk_articles_link unique (link);

-- Article 테이블 인덱스 추가
create index idx_articles_source on articles (source);
create index idx_articles_date on articles (date desc);
create index idx_articles_source_date on articles (source, date desc);

-- ArticleView 테이블 수정: created_at 추가
alter table article_views
    add column created_at timestamp default current_timestamp not null;

-- ArticleView 테이블 UNIQUE 제약 추가
alter table article_views
    add constraint uk_article_views_article_user unique (article_id, user_id);

-- ArticleView 테이블 인덱스 추가
create index idx_article_views_user_id on article_views (user_id);
create index idx_article_views_viewed_at on article_views (viewed_at desc);