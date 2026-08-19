-- 사용자 이메일 중복 방지를 위한 UNIQUE 제약 추가
alter table users
    add constraint uk_users_email unique (email);