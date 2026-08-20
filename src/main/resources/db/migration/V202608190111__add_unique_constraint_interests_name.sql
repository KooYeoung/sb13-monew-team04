-- 관심사 이름 중복 방지를 위한 UNIQUE 제약 추가
alter table interests
    add constraint uk_interests_name unique (name);
