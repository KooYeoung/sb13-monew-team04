-- Subscribe 엔티티가 BaseEntity 대신 CreatedAtEntity를 상속하도록 변경됨에 따라,
-- subscriptions 테이블에도 구독 시각을 기록하는 created_at 컬럼을 추가한다.
-- 이미 존재하는 행에는 마이그레이션 적용 시점을 임시 값으로 채우고, 이후부터는
-- Subscribe 엔티티의 @CreatedDate가 실제 구독 시각을 채우므로 default는 제거한다.
alter table subscriptions
    add column created_at timestamp not null default now();

alter table subscriptions
    alter column created_at drop default;
