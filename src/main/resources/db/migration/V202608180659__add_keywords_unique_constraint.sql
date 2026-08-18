-- Keyword 엔티티의 @UniqueConstraint(name = "uk_keywords_interest_keyword")와
-- 일치시키기 위해, keywords 테이블의 (interest_id, keyword) 조합에
-- 유니크 제약조건을 추가한다. 같은 관심사에 동일한 키워드가
-- 중복으로 등록되는 것을 데이터베이스 레벨에서 막는다.
alter table keywords
    add constraint uk_keywords_interest_keyword unique (interest_id, keyword);
