# MID4-148 뉴스 기사 S3 백업 및 복구 기준

## 작업 기준

- Jira: `MID4-148`
- Parent: `MID4-74`
- 현재 작업 티켓: `MID4-169`
- PR 매핑: `MID4-166` `PR #45`, `MID4-167` `PR #47`, `MID4-168` `PR #48`, `MID4-169` `PR #57`
- 목적: 뉴스 기사 데이터를 날짜 단위로 S3에 백업하고, 물리적으로 유실된 기사만 DB에 복구한다.
- 구현 범위: 날짜 단위 백업 기준, AWS S3 설정 기준, Spring Scheduler 배치, PostgreSQL advisory lock 동시성 제어, 날짜 범위 복구 API, 실패 로그 기준
- `MID4-166` 범위: AWS S3 설정, 환경변수, credentials provider, Storage 인터페이스, test profile 대체 구현
- `MID4-167` 범위: 기사 백업 파일 모델, 백업 아이템 모델, JSON 직렬화/역직렬화 컨버터, 검증 테스트 구현

## 날짜 단위 백업 기준

`Article.date`는 Java 기준 `LocalDateTime`이고 DB 기준 `TIMESTAMP`다.

백업 단위는 `Article.date`의 날짜 부분인 `LocalDate` 기준으로 둔다. 백업일 `D`의 조회 범위는 아래와 같이 해석한다.

```text
D 00:00:00 <= Article.date < D+1 00:00:00
```

예를 들어 `2026-08-23` 백업은 아래 범위의 기사를 대상으로 한다.

```text
2026-08-23T00:00:00 <= Article.date < 2026-08-24T00:00:00
```

timezone 변환은 적용하지 않는다. 애플리케이션과 DB에 저장된 `LocalDateTime` 값을 그대로 사용해 날짜 부분만 자른다.

백업 대상의 논리삭제 포함 여부는 이 문서에서 고정하지 않는다. 날짜 단위 백업의 필수 기준은 `Article.date` 범위이며, `deleted_at` 포함/제외 여부는 구현 시점의 운영 정책에 맞춰 결정한다.

## S3 저장 기준

백업 파일은 날짜별 단일 JSON 파일로 저장한다.

```text
{prefix}/yyyy-MM-dd.json
```

기본 prefix는 `article-backups`로 둔다.

```text
article-backups/2026-08-23.json
```

Storage 계층에는 문자열 key가 아니라 백업 기준 날짜 `LocalDate`를 전달한다. 예를 들어 `StorageCommand.backupDate` 또는 `StorageSearchCommand.backupDate`에는 `2026-08-23`을 전달하고, 실제 S3 요청 key는 `monew.s3.prefix`와 날짜를 조합해 `article-backups/2026-08-23.json`으로 만든다.

백업 파일은 UTF-8 JSON으로 저장한다. 1차 schema는 다음 형태로 둔다.

```json
{
  "schemaVersion": 1,
  "backupDate": "2026-08-23",
  "generatedAt": "2026-08-24T00:10:00",
  "articleCount": 2,
  "articles": [
    {
      "originalArticleId": "00000000-0000-4000-8000-000000000001",
      "source": "NAVER",
      "link": "https://example.com/news/1",
      "title": "기사 제목",
      "summary": "기사 요약",
      "publishedAt": "2026-08-23T10:15:00",
      "deletedAt": null
    }
  ]
}
```

필드 기준:

| 필드 | 기준 |
| --- | --- |
| `schemaVersion` | 백업 파일 구조 변경 대비용 버전 |
| `backupDate` | 백업 기준 날짜 |
| `generatedAt` | 백업 파일 생성 시각 |
| `articleCount` | `articles` 배열 크기 |
| `originalArticleId` | 백업 시점의 원본 Article ID, 복구 시 DB ID로 재사용하지 않음 |
| `source` | `ArticleSource` enum 문자열 |
| `link` | 기사 원문 URL, 복구 중복 판단 기준 |
| `title` | 기사 제목 |
| `summary` | 기사 요약 |
| `publishedAt` | `Article.date` 값 |
| `deletedAt` | 백업 대상에 논리삭제 기사가 포함될 경우 상태 확인용, 1차 복구 중복 판단에는 사용하지 않음 |

같은 S3 key에 이미 백업 파일이 존재하는 경우 기본적으로 overwrite하지 않는다. 이 정책은 동시성 제어가 아니라 실수로 기존 백업 파일을 덮어쓰지 않기 위한 저장 정책이다. Storage 계층의 저장 계약은 `saveIfAbsent`이며, S3 `If-None-Match: *` 조건부 저장으로 기존 객체 덮어쓰기를 방지한다. 재백업이 필요하면 별도 운영 절차나 강제 옵션을 둔다.

## AWS S3 설정 기준

S3 bucket, region, endpoint, prefix는 환경변수로 설정한다.

```text
MONEW_AWS_REGION=ap-northeast-2
MONEW_AWS_ENDPOINT=
MONEW_ARTICLE_BACKUP_S3_BUCKET=
MONEW_ARTICLE_BACKUP_S3_PREFIX=article-backups
```

실제 AWS S3를 사용할 때는 AWS SDK default credentials provider chain을 사용한다.

로컬 또는 CI 환경에서는 아래 중 하나를 사용한다.

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN` optional
- IAM Role 또는 profile 기반 인증

로컬 S3Mock endpoint를 사용할 때는 실제 AWS 자격증명 없이 테스트할 수 있도록 `AnonymousCredentialsProvider`를 사용한다. 이 설정은 `MONEW_AWS_ENDPOINT`가 지정된 로컬 테스트용 예외다.

access key와 secret은 코드, 문서 예시값, commit에 포함하지 않는다. `.env.example`에는 변수명과 빈 기본값만 추가한다.

## 스케줄 백업 기준

백업 작업은 Spring Scheduler로 daily job을 둔다.

스케줄러 실행 여부와 cron은 `monew.backup.schedule` 설정으로 분리한다. 환경변수가 없으면 `application.yaml` fallback 값을 사용한다.

| 설정 | 환경변수 | 기본값 |
| --- | --- | --- |
| `monew.backup.schedule.enabled` | `MONEW_ARTICLE_BACKUP_ENABLED` | `false` |
| `monew.backup.schedule.cron` | `MONEW_ARTICLE_BACKUP_CRON` | `0 10 0 * * *` |

기본 scheduler는 실행일 기준 전날의 기사 발행일을 백업 대상으로 잡는다.

```text
실행일: 2026-08-24T00:10:00
백업 대상 날짜: 2026-08-23
S3 key: article-backups/2026-08-23.json
```

수동 실행이나 테스트에서는 특정 `backupDate`를 직접 전달할 수 있는 service 메서드를 둔다.

## 동시성 제어 기준

백업 scheduler는 PostgreSQL advisory lock으로 같은 날짜 백업 실행을 직렬화한다.

lock key는 job 이름과 백업 대상 날짜를 조합한 문자열을 기준으로 만든다.

```text
article-backup:2026-08-23
```

실제 PostgreSQL advisory lock은 숫자 key를 사용하므로, 구현 시 문자열 lock key를 안정적인 `long` 또는 `(int, int)` key로 변환한다.

동작 흐름:

1. scheduler가 백업 대상 날짜를 계산한다.
2. `article-backup:{backupDate}` 기준 advisory lock 획득을 시도한다.
3. lock 획득에 성공한 실행자만 백업 메서드를 수행한다.
4. lock 획득에 실패한 실행자는 백업을 수행하지 않고 WARN 로그를 남긴다.
5. 백업 성공/실패와 관계없이 lock을 해제한다.

WARN 로그 예시:

```text
이미 다른 스케줄러가 기사 백업을 실행 중입니다. backupDate=2026-08-23
```

1차 기준은 PostgreSQL advisory lock이다. Redis가 도입되면 같은 lock key 정책을 Redis 분산락으로 대체할 수 있다.

S3 object 존재 여부나 conditional put은 scheduler 동시성 제어 수단으로 사용하지 않는다. S3 조건부 저장은 overwrite 방지 정책에서만 사용한다.

## 복구 기준

복구 API는 지정 날짜 범위의 S3 백업 파일을 읽고, 현재 DB에 없는 기사만 새로 저장한다.

```http
GET /api/articles/restore?from=2026-08-23&to=2026-08-24
```

`from`, `to`는 inclusive 날짜 범위다.

```text
from=2026-08-23
to=2026-08-24
복구 대상 백업 파일:
- article-backups/2026-08-23.json
- article-backups/2026-08-24.json
```

복구 응답은 날짜별 결과 목록으로 둔다.

```json
[
  {
    "restoreDate": "2026-08-23",
    "restoredArticleIds": [
      "11111111-1111-4111-8111-111111111111"
    ],
    "restoredArticleCount": 1
  }
]
```

복구 흐름:

1. 날짜 범위를 순회한다.
2. 날짜별 S3 백업 파일을 읽는다.
3. JSON을 `ArticleBackupFile`, `ArticleBackupItem` 형태로 객체화한다.
4. 각 item의 `link`로 현재 DB 존재 여부를 확인한다.
5. 같은 `link`가 없으면 새 `Article`로 저장한다.
6. 같은 `link`가 있으면 복구하지 않는다.
7. 저장된 Article ID를 `restoredArticleIds`에 담는다.

복구는 전체 덮어쓰기가 아니다. `link` 기준 insert-if-absent upsert로 본다.

```sql
-- 개념 기준
INSERT INTO articles (...)
VALUES (...)
ON CONFLICT (link) DO NOTHING;
```

JPA 구현에서는 `findByLink()`로 1차 확인 후 저장하고, 동시 복구로 `uk_articles_link` unique 충돌이 발생하면 이미 복구된 것으로 보고 skip한다.

## 논리삭제와 물리삭제 복구 기준

현재 DB에 같은 `link`가 존재하면 논리삭제 상태여도 복구하지 않는다.

즉, 아래 경우는 복구 대상이 아니다.

```text
S3 백업에 link=A가 있음
DB articles에 link=A, deleted_at IS NOT NULL row가 있음
결과: 복구하지 않음
```

물리삭제된 기사는 DB에 같은 `link`가 없으므로 S3 백업에 있으면 복구 대상이다.

```text
S3 백업에 link=A가 있음
DB articles에 link=A row가 없음
결과: 새 Article로 복구
```

복구 시 `originalArticleId`는 재사용하지 않는다. `Article.id`는 현재 JPA `GenerationType.UUID` 기준으로 새로 발급한다. `restoredArticleIds`는 새로 저장된 Article ID 목록이다.

## 실패 로깅 기준

백업 실패 시에는 날짜, S3 key, 원인을 로그에 남긴다.

```text
기사 백업에 실패했습니다. backupDate=2026-08-23, key=article-backups/2026-08-23.json
```

복구 실패 시에는 날짜, S3 key, 원인을 로그에 남긴다.

```text
기사 복구에 실패했습니다. restoreDate=2026-08-23, key=article-backups/2026-08-23.json
```

advisory lock 획득 실패는 장애로 보지 않고 WARN 로그 후 skip한다.

Storage 계층에서 S3 객체가 없을 때는 장애로 보지 않는다. `find`는 `Optional.empty()`를 반환하고, `exists`는 `false`를 반환하며 WARN 로그를 남긴다. 백업 파일이 없을 때 복구 API를 실패로 볼지, 해당 날짜 복구 건수 0으로 볼지는 후속 복구 티켓에서 결정한다.

S3 read/write 실패, JSON parse 실패, 필수 필드 누락은 복구 또는 백업 실패로 본다.

## 하위 작업 분리

MID4-148은 전체 기능 기준으로 유지하고, 구현은 아래 하위 작업 단위로 분리한다.

| 티켓 | 범위 | PR |
| --- | --- | --- |
| `MID4-166` | 기사 S3 백업 설정 및 Storage 인터페이스 구성 | `PR #45` |
| `MID4-167` | 기사 백업 파일 모델 및 JSON 직렬화 구현 | `PR #47` |
| `MID4-168` | 날짜 단위 기사 백업 서비스 구현 | `PR #48` |
| `MID4-169` | 기사 백업 Scheduler 및 PostgreSQL advisory lock 적용 | `PR #57` |
| `MID4-170` | 날짜 범위 기사 복구 서비스 구현 | - |
| `MID4-171` | 기사 복구 API 및 응답 DTO 구현 | - |

## 구현 전 확인할 사항

- 실제 S3 bucket 이름과 region
- GitHub Actions Secrets 등록 여부
- 백업 대상에 논리삭제 기사를 포함할지 여부
- 백업 파일이 없을 때 복구 API를 실패로 볼지, 해당 날짜 복구 건수 0으로 볼지
- 강제 재백업 또는 overwrite 옵션을 제공할지 여부
