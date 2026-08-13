-- 공통 설정: UUID 자동 생성을 위한 PostgreSQL 확장
create extension if not exists pgcrypto;

-- 사용자 도메인: 회원 기본 정보
create table users (
    id uuid default gen_random_uuid() not null,
    email varchar(255) not null,
    nickname varchar(60) not null,
    password varchar(225) not null,
    created_at timestamp not null,
    updated_at timestamp null,
    deleted_at timestamp null,
    constraint pk_users primary key (id)
);

-- 관심사 도메인: 관심사, 키워드, 구독
create table interests (
    id uuid default gen_random_uuid() not null,
    name varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint pk_interests primary key (id)
);

create table keywords (
    id uuid default gen_random_uuid() not null,
    interest_id uuid not null,
    keyword varchar(50) not null,
    constraint pk_keywords primary key (id),
    constraint fk_keywords_interest_id foreign key (interest_id) references interests (id)
);

create table subscriptions (
    id uuid default gen_random_uuid() not null,
    interest_id uuid not null,
    user_id uuid not null,
    constraint pk_subscriptions primary key (id),
    constraint fk_subscriptions_interest_id foreign key (interest_id) references interests (id),
    constraint fk_subscriptions_user_id foreign key (user_id) references users (id)
);

-- 뉴스 도메인: 기사와 기사 조회 이력
create table articles (
    id uuid default gen_random_uuid() not null,
    title varchar(500) not null,
    summary text not null,
    link varchar(1000) not null,
    date timestamp not null,
    source varchar(50) not null,
    created_at timestamp default current_timestamp null,
    updated_at timestamp default current_timestamp null,
    constraint pk_articles primary key (id)
);

create table article_views (
    id uuid default gen_random_uuid() not null,
    article_id uuid not null,
    user_id uuid not null,
    viewed_at timestamp default current_timestamp null,
    constraint pk_article_views primary key (id),
    constraint fk_article_views_article_id foreign key (article_id) references articles (id),
    constraint fk_article_views_user_id foreign key (user_id) references users (id)
);

-- 댓글 도메인: 댓글과 댓글 좋아요
create table comments (
    id uuid default gen_random_uuid() not null,
    article_id uuid not null,
    user_id uuid not null,
    content varchar(500) not null,
    created_at timestamp not null,
    updated_at timestamp null,
    deleted_at timestamp null,
    constraint pk_comments primary key (id),
    constraint fk_comments_article_id foreign key (article_id) references articles (id),
    constraint fk_comments_user_id foreign key (user_id) references users (id)
);

create table comment_likes (
    id uuid default gen_random_uuid() not null,
    comment_id uuid not null,
    liked_by uuid not null,
    created_at timestamp not null,
    constraint pk_comment_likes primary key (id),
    constraint fk_comment_likes_comment_id foreign key (comment_id) references comments (id),
    constraint fk_comment_likes_liked_by foreign key (liked_by) references users (id)
);

-- 알림 도메인: 사용자 알림
create table notifications (
    id uuid default gen_random_uuid() not null,
    user_id uuid not null,
    content varchar(100) not null,
    resource_id uuid not null,
    resource_type varchar(20) not null,
    confirmed boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint pk_notifications primary key (id),
    constraint fk_notifications_user_id foreign key (user_id) references users (id)
);
