-- Subscribe 엔티티의 @UniqueConstraint(name = "uk_subscriptions_interest_user")와
-- 일치시키기 위해, subscriptions 테이블의 (interest_id, user_id) 조합에
-- 유니크 제약조건을 추가한다. 같은 사용자가 같은 관심사를 중복 구독하는 것을
-- 데이터베이스 레벨에서 막는다.
alter table subscriptions
    add constraint uk_subscriptions_interest_user unique (interest_id, user_id);
