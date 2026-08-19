# Flyway 마이그레이션 가이드

이 문서는 Monew 프로젝트에서 DB 스키마를 Flyway로 관리하는 방법을 정리합니다.

## 핵심 원칙

- 초기 공통 스키마는 `V1__init_schema.sql` 한 파일에서 관리합니다.
- `V1` 안에서는 사용자, 관심사, 뉴스, 댓글, 알림처럼 도메인별 주석으로 영역을 구분합니다.
- 한 번 공유되었거나 DB에 적용된 마이그레이션 파일은 수정하지 않습니다.
- 이후 변경 사항은 항상 새로운 마이그레이션 파일로 추가합니다.
- `dev`, `prod` 환경에서는 Hibernate `ddl-auto: validate`로 엔티티와 DB 스키마 일치 여부를 확인합니다.
- 엔티티의 테이블, 컬럼, 인덱스, 제약조건 변경은 Flyway 마이그레이션과 함께 작성합니다.

## 파일 위치

Flyway SQL 파일은 아래 경로에 둡니다.

```text
src/main/resources/db/migration/
```

현재 초기 스키마 파일은 다음 파일입니다.

```text
src/main/resources/db/migration/V1__init_schema.sql
```

## 파일 이름 규칙

Flyway 파일명은 아래 형식을 사용합니다.

```text
V{version}__{description}.sql
```

예시:

```text
V1__init_schema.sql
V202608131130__add_article_source_index.sql
V202608141020__add_notification_resource_id.sql
```

작성 기준:

- 버전과 설명 사이에는 밑줄 2개(`__`)를 사용합니다.
- `V1` 이후에는 충돌을 줄이기 위해 timestamp 버전을 사용합니다.
- 설명은 영어 소문자와 밑줄을 사용합니다.

권장 형식:

```text
VyyyyMMddHHmm__description.sql
```

## 초기 스키마 작성 방식

초기 공통 테이블은 여러 파일로 나누지 않고 `V1__init_schema.sql` 한 파일에서 관리합니다.

도메인별로 필요한 내용만 짧게 주석을 남깁니다.

```sql
-- 사용자 도메인: 회원 기본 정보
-- 관심사 도메인: 관심사, 키워드, 구독
-- 뉴스 도메인: 기사와 기사 조회 이력
-- 댓글 도메인: 댓글과 댓글 좋아요
-- 알림 도메인: 사용자 알림
```

초기 파일을 여러 개로 나누면 FK 참조 순서와 버전 순서를 함께 관리해야 합니다. 따라서 초기 공통 구조는 한 명이 `V1`로 정리하고, 공유 이후부터 각 담당자가 새 마이그레이션 파일을 추가합니다.

## 테이블과 컬럼 기준

DB 식별자는 따옴표 없이 `lower_snake_case`를 사용합니다.

좋은 예:

```sql
user_id
created_at
interest_id
resource_type
```

피해야 할 예:

```sql
"userId"
"createdAt"
"interestId"
```

PostgreSQL에서 `user`는 예약어와 충돌할 수 있으므로 `"user"` 대신 `users`를 사용합니다.

## UUID 기준

PK 컬럼은 DB에서 UUID를 자동 생성합니다.

```sql
id uuid default gen_random_uuid() not null
```

이를 위해 초기 마이그레이션 맨 위에 PostgreSQL 확장을 추가합니다.

```sql
create extension if not exists pgcrypto;
```

FK 컬럼에는 UUID 자동 생성을 걸지 않습니다. FK는 이미 존재하는 다른 테이블의 PK 값을 참조해야 합니다.

```sql
user_id uuid not null
article_id uuid not null
```

## FK 기준

명확하게 참조 대상이 정해진 관계는 FK를 연결합니다.

초기 스키마의 주요 관계:

```text
keywords.interest_id -> interests.id
subscriptions.interest_id -> interests.id
subscriptions.user_id -> users.id
article_views.article_id -> articles.id
article_views.user_id -> users.id
comments.article_id -> articles.id
comments.user_id -> users.id
comment_likes.comment_id -> comments.id
comment_likes.liked_by -> users.id
notifications.user_id -> users.id
```

`notifications.resource_id`는 `resource_type`에 따라 article, comment 등 여러 리소스를 가리킬 수 있습니다. 하나의 테이블로 참조 대상이 고정되지 않기 때문에 초기 스키마에서는 FK를 걸지 않습니다.

## 인덱스와 UNIQUE 제약 기준

엔티티에 인덱스나 UNIQUE 제약을 선언할 때는 DB에 생성될 이름을 명시합니다.

```java
@Table(
    name = "article_views",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_article_views_article_user",
            columnNames = {"article_id", "user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_article_views_user_viewed", columnList = "user_id, viewed_at DESC")
    }
)
```

`@Column(unique = true)`는 제약 이름을 명확히 남기기 어렵기 때문에 사용하지 않습니다.

좋은 예:

```java
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
```

피해야 할 예:

```java
@Column(unique = true)
private String email;
```

엔티티에 선언한 제약 이름과 Flyway SQL에 작성한 제약 이름은 같아야 합니다.

UNIQUE 제약, 인덱스, FK 변경은 자동 테스트만으로 누락 여부를 보장하기 어렵습니다. PR 리뷰에서 엔티티 변경과 Flyway SQL 변경이 함께 들어갔는지 확인합니다.

## 변경 작업 흐름

스키마 변경이 필요하면 기존 파일을 수정하지 않고 새 파일을 추가합니다.

1. 엔티티 변경 내용을 먼저 정리합니다.
2. DB 변경 내용을 새 Flyway SQL 파일로 작성합니다.
3. 파일명은 `VyyyyMMddHHmm__description.sql` 형식을 사용합니다.
4. 로컬 DB에서 마이그레이션 적용 여부를 확인합니다.
5. PR에는 엔티티 변경과 마이그레이션 파일을 함께 포함합니다.

## 로컬 실행 기준

`.env.dev`가 없다면 예시 파일을 복사합니다.

```powershell
Copy-Item .env.example .env.dev
```

기본 로컬 DB 설정은 다음 값을 사용합니다.

```properties
MONEW_DB_HOST=localhost
MONEW_DB_PORT=5432
MONEW_DB_NAME=monew
MONEW_DB_USERNAME=monew
MONEW_DB_PASSWORD=change-me
MONEW_FLYWAY_ENABLED=true
```

이미 로컬 DB에 수동으로 만든 테이블이 있으면 초기 마이그레이션과 충돌할 수 있습니다. 초기 개발 단계에서는 필요하면 로컬 DB를 새로 만들거나 Docker volume을 정리한 뒤 다시 실행합니다.

## 테스트 환경

기본 `test` profile에서는 Flyway를 실행하지 않습니다.

```yaml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: create-drop
```

테스트 실행:

```powershell
.\gradlew.bat test
```

Flyway 자체 동작이나 DB catalog 기반 제약 검증이 필요해지면 별도 Jira 티켓으로 분리해 검증 범위를 먼저 정합니다.

## .gitkeep 사용 기준

Git은 빈 디렉터리를 추적하지 않습니다.

SQL 파일이 아직 없고 마이그레이션 디렉터리만 먼저 공유해야 한다면 `.gitkeep`을 둘 수 있습니다.

```text
src/main/resources/db/migration/.gitkeep
```

현재는 `V1__init_schema.sql`이 있으므로 `.gitkeep`은 필요하지 않습니다.
