# MID4-132 활동내역 RDB baseline 측정

## 요약

활동내역 RDB 조회의 SQL baseline, 단일 사용자 활동내역 API baseline, 병목 후보를 정리한다. SQL 원문과 EXPLAIN은 조회별 상세 문서에 남기고, API p95/p99, error rate, DB 부하는 [api-baseline.md](api-baseline.md)에 기록한다.

## Seed Scale 정의

이 문서의 `100k`, `1m`, `10m`은 각 테이블 row 수가 아니라 `seed_activity_history(scale_count)`에 전달한 seed scale이다. 실제 row 수는 seed 함수에서 도메인 분포에 맞춰 파생된다.

- `articles`: `scale_count / 5`
- `comments`: `scale_count * 0.4`
- `comment_likes`: `scale_count * 0.3`
- `article_views`: `scale_count - comments - comment_likes`
- target user의 최근 댓글, 좋아요, 조회 row 수: `scale_count / 100`, 최소 `1,000`, 최대 `10,000`

따라서 `10m seed scale`은 각 테이블이 1,000만 건이라는 뜻이 아니며, 실제 테이블별 row count는 Seed 결과 표에 따로 기록한다.

## 상세 문서

| 구분 | 상세 문서 | 기준 결과 | 1차 후보 또는 판단 |
| --- | --- | ---: | --- |
| 단일 활동내역 API | [api-baseline.md](api-baseline.md) | `1m p95 474.02 ms`, `10m dropped 979` | 100k/1m seed scale은 20 rps 통과, 10m seed scale은 처리량/latency 기준 실패 |
| 최근 작성 댓글 | [recent-comments.md](recent-comments.md) | `10m median 82.747 ms` | `comments(user_id, created_at DESC, id DESC)` |
| 최근 좋아요한 댓글 | [recent-liked-comments.md](recent-liked-comments.md) | `10m median 45.905 ms` | `comment_likes(liked_by, created_at DESC, id DESC)` |
| 최근 조회 기사 | [recent-article-views.md](recent-article-views.md) | `10m median 1825.932 ms` | `article_views(user_id, viewed_at DESC, id DESC)`, `comments(article_id)` |
| 구독 중인 관심사 | [subscribed-interests.md](subscribed-interests.md) | `10m median 11.635 ms` | `subscriptions(user_id, created_at DESC, id DESC)` |

## API Baseline 요약

측정 대상은 `GET /api/user-activities/{userId}`이다. 측정 당시에는 PR #69의 k6 스크립트를 stdin으로 주입해 실행했고, 현재 해당 스크립트는 develop에 병합되어 `scripts/performance/activity-history/k6/activity-history-baseline.js`에 포함되어 있다.

| seed scale | seed 소요 시간 | requests | RPS | duration avg | duration p95 | duration p99 | error rate | dropped iterations | 판단 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `100k` | `3.497 s` | `1201` | `20.01` | `46.20 ms` | `53.58 ms` | `63.40 ms` | `0.00%` | `0` | pass |
| `1m` | `15.027 s` | `1201` | `19.97` | `379.20 ms` | `474.02 ms` | `657.60 ms` | `0.00%` | `0` | pass |
| `10m` | `129.231 s` | `222` | `3.11` | `28084.62 ms` | `32353.24 ms` | `43167.63 ms` | `0.00%` | `979` | fail |

10m seed scale baseline은 k6가 max VUs `100`에 도달했고 `droppedIterations=979`가 발생해 20 req/s 요청 스케줄을 따라가지 못했다. baseline 실행 중 PostgreSQL 컨테이너는 1m에서 `CPU 1020.92%`, 10m에서 `CPU 939.03%`, `MEM 2.344 GiB / 30.91 GiB`까지 관측됐다. seed scale별 DB stats는 [api-baseline.md](api-baseline.md)에 기록한다.

요청 1건 기준 SQL은 코드 경로상 6개로 본다.

| 순서 | 조회 | 주요 join 또는 subquery |
| ---: | --- | --- |
| 1 | 사용자 조회 | `users` PK 조회, `deleted_at` 확인 |
| 2 | 최근 본 기사 | `article_views -> articles -> users`, 댓글 수 subquery, 조회수 subquery |
| 3 | 최근 작성 댓글 | `comments -> users -> articles`, 좋아요 수 subquery |
| 4 | 최근 좋아요 댓글 | `comment_likes -> comments -> users -> articles`, 좋아요 수 subquery |
| 5 | 구독 관심사 main | `subscriptions -> interests -> users`, 관심사별 구독자 수 subquery |
| 6 | 구독 관심사 keywords | `keywords.interest_id = any (?)` batch 조회 |

## 인덱스 후보 요약

| 조회 | 병목 | 후보 | 비고 |
| --- | --- | --- | --- |
| 최근 작성 댓글 | `comments.user_id` 접근 경로 부재, 최신순 정렬 | `comments(user_id, created_at DESC, id DESC)` | `deleted_at IS NULL`은 1차 후보에서 제외 |
| 최근 좋아요한 댓글 | `comment_likes.liked_by` 접근 경로 부재, 최신순 정렬 | `comment_likes(liked_by, created_at DESC, id DESC)` | 기존 `(comment_id, liked_by)` unique index는 선두 컬럼이 맞지 않음 |
| 최근 조회 기사 | `article_views.user_id` 접근 경로 부재, 최신순 정렬 | `article_views(user_id, viewed_at DESC, id DESC)` | 기존 article_id 선두 인덱스들은 main query에 부적합, `idx_article_views_article_viewed`는 후속 사용처 확인 후 제거 검토 |
| 최근 조회 기사 댓글 수 | `comments.article_id` 접근 경로 부재 | `comments(article_id)` | 댓글 수 subquery의 반복 full scan 제거 후보 |
| 구독 중인 관심사 | `subscriptions.user_id` 접근 경로 부재 | `subscriptions(user_id, created_at DESC, id DESC)` | 정렬 자체보다 `user_id` full scan이 병목 후보. 정렬 전 후보였던 `subscriptions(user_id)`, `subscriptions(user_id, interest_id)`는 용량/효과 비교용 |

## 측정 기준

- 대표 Jira: `MID4-132`
- Parent: `MID4-77`
- 관련 Jira: `MID4-92` 구독 중인 관심사 조회, `MID4-130` 시드 데이터
- 후속 Jira: `MID4-125`, `MID4-133`
- 문서 브랜치: `docs/MID4-132-activity-history-rdb-baseline`
- seed/table snapshot 시각: 2026-08-24 13:27:19 +09:00
- 최근 활동 3종 actual SQL 측정 시각: 2026-08-23 10:23:40 +09:00
- 구독 중인 관심사 actual SQL 측정 시각: 2026-08-24 14:08:45 +09:00
- API baseline 측정 시각: 2026-08-25 17:00-17:57 KST
- 최근 활동 3종 기준 커밋: `f7b198a`
- 구독 중인 관심사 기준 커밋: `6ae5754`
- API baseline 측정 당시 앱 기준: `origin/develop` `ffed6e1` 위에 MID4-132 문서 브랜치 rebase
- 현재 문서 브랜치 base: `origin/develop` `5bc6c4e` (PR #69, PR #71 반영)
- k6 스크립트 기준: `scripts/performance/activity-history/k6/activity-history-baseline.js` (PR #69 병합, 측정 당시 source `5a5d761`)
- SQL baseline 측정 DB project: `monew-perf-rerun`
- SQL baseline PostgreSQL container: `monew-perf-rerun-postgres-1`
- SQL baseline Port: `5435 -> 5432`
- API baseline 측정 DB project: `monew-perf-132`
- API baseline PostgreSQL container: `monew-perf-132-postgres-1`
- API baseline Port: `5433 -> 5432`
- Docker image: `postgres:16`
- PostgreSQL version: `PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)`
- Docker engine: `linux x86_64`, CPU `24`, memory 약 `30.9 GiB`
- 대상 사용자 ID: `00000001-0000-4000-8000-000000000001`

측정 실행 세트:

| 측정 세트 | 대상 | 출처 | 측정 시각 | seed 소요 시간 |
| --- | --- | --- | --- | --- |
| seed/table snapshot | Seed 결과, 테이블 크기 | DB catalog 조회 | 2026-08-24 13:27 KST | `100k` `3.254 s`, `1m` `15.047 s`, `10m` `129.118 s` |
| 최근 활동 3종 actual SQL | 최근 작성 댓글, 최근 좋아요한 댓글, 최근 조회 기사 | Hibernate SQL 로그 | 2026-08-23 10:23 KST | `100k` `3.214 s`, `1m` `14.048 s`, `10m` `124.057 s` |
| 구독 중인 관심사 actual SQL | 구독 중인 관심사 main, keywords | Hibernate SQL 로그 | 2026-08-24 14:08 KST | `100k` `3.254 s`, `1m` `15.047 s`, `10m` `129.118 s` |
| 단일 API baseline | `/api/user-activities/{userId}` | k6 summary, PostgreSQL stats | 2026-08-25 17:00-17:57 KST | `100k` `3.497 s`, `1m` `15.027 s`, `10m` `129.231 s` |

아래 Seed 결과 테이블은 `subscriptions.created_at` 반영 후 데이터를 다시 생성한 snapshot 실행값이다. 실제 조회 SQL 측정은 각 query set마다 데이터를 다시 생성한 뒤 수행했으므로 최근 활동 3종의 seed 소요 시간은 snapshot과 약간 다르다.

최근 활동 3종 조회는 Hibernate SQL 로그로 확인한 actual SQL 기준으로 측정했다.

구독 중인 관심사 조회도 Hibernate SQL 로그로 확인한 actual SQL 기준으로 측정했다. 구독 생성일자 정렬 반영 후 다시 측정했으며, main query와 keywords batch query를 요청 1건 기준으로 함께 기록했다. 현재 구현은 develop에 반영된 `SubscribeRepository.findSubscribedInterestActivities()` 기준이다.

PostgreSQL 주요 설정:

| 설정 | 값 |
| --- | ---: |
| `shared_buffers` | `16384 8kB` 약 `128 MB` |
| `work_mem` | `4096 kB` 약 `4 MB` |
| `effective_cache_size` | `524288 8kB` 약 `4 GB` |
| `max_connections` | `100` |

현재 DB 성능은 별도 `pgbench` TPS가 아니라, 위 로컬 Docker PostgreSQL 환경에서 활동내역 조회 SQL이 실제로 보인 latency와 실행계획을 기준으로 판단한다.

## Schema 확인

fresh DB에 적용된 Flyway migration:

```text
1 | 1 | init schema | true
2 | 202608180652 | add subscriptions unique constraint | true
3 | 202608180659 | add keywords unique constraint | true
4 | 202608181717 | add unique constraint users email | true
5 | 202608182356 | add article constraints and indexes | true
6 | 202608182357 | add comment likes unique constraint | true
7 | 202608190001 | drop article views user viewed index | true
8 | 202608190111 | add unique constraint interests name | true
9 | 202608240944 | add subscriptions created at | true
```

측정 전 적용 인덱스:

아래 목록은 `pg_indexes`에서 직접 조회한 baseline 기준 인덱스다. `notifications`는 이번 측정 SQL에 직접 등장하지 않아 목록에 포함하지 않았다.

| Table | Index | Columns |
| --- | --- | --- |
| `comments` | `pk_comments` | `id` |
| `comment_likes` | `pk_comment_likes` | `id` |
| `comment_likes` | `uk_comment_likes_comment_liked_by` | `comment_id, liked_by` |
| `users` | `pk_users` | `id` |
| `users` | `uk_users_email` | `email` |
| `articles` | `pk_articles` | `id` |
| `articles` | `uk_articles_link` | `link` |
| `articles` | `idx_articles_source` | `source` |
| `articles` | `idx_articles_date` | `date DESC` |
| `articles` | `idx_articles_source_date` | `source, date DESC` |
| `article_views` | `pk_article_views` | `id` |
| `article_views` | `uk_article_views_article_user` | `article_id, user_id` |
| `article_views` | `idx_article_views_article_viewed` | `article_id, viewed_at DESC` |
| `interests` | `pk_interests` | `id` |
| `interests` | `uk_interests_name` | `name` |
| `subscriptions` | `pk_subscriptions` | `id` |
| `subscriptions` | `uk_subscriptions_interest_user` | `interest_id, user_id` |
| `keywords` | `pk_keywords` | `id` |
| `keywords` | `uk_keywords_interest_keyword` | `interest_id, keyword` |

PostgreSQL에서는 FK 선언만으로 referencing column 인덱스가 자동 생성되지 않는다. `V1__init_schema.sql`의 `fk_comment_likes_comment_id`는 참조 무결성만 보장한다. 다만 현재 schema에는 `(comment_id, liked_by)` unique constraint가 적용되어 있고, 이 제약이 생성한 unique index를 `comment_id = ...` 조건에서 사용할 수 있다.

구독도 같은 기준이다. `subscriptions`에는 `(interest_id, user_id)` unique index가 있으므로 `interest_id = ...` 조건에는 사용할 수 있지만, `user_id = ...` 단독 조건에는 선두 컬럼이 맞지 않아 바로 사용할 수 없다.

MySQL InnoDB는 PostgreSQL과 다르게 FK referencing column에 적절한 인덱스가 없으면 자동으로 생성한다. 이번 측정은 PostgreSQL 기준이다.

## 측정 방법

각 seed scale마다 아래 순서로 진행했다.

1. `postgres-seed`로 데이터를 처음부터 재생성한다.
2. seed 함수 끝에서 대상 테이블을 `ANALYZE`해 통계 정보를 갱신한다.
3. seed 소요 시간은 PowerShell `Measure-Command`로 측정한다.
4. 각 조회 SQL을 3회 warm-up 실행한다.
5. `EXPLAIN (ANALYZE, BUFFERS)`로 실행계획을 확인한다.
6. 같은 SQL을 5회 반복 실행하고 psql `\timing` 결과의 median을 대표값으로 기록한다.

대량 데이터를 넣은 뒤 통계 정보가 갱신되어야 PostgreSQL optimizer가 실제 데이터 분포에 가까운 실행계획을 선택할 수 있으므로, seed 함수에서 `users`, `interests`, `keywords`, `subscriptions`, `articles`, `comments`, `comment_likes`, `article_views`를 `ANALYZE`했다.

최근 작성 댓글, 최근 좋아요한 댓글, 최근 조회 기사 조회는 `test/MID4-77-activity-history-integration-check` 브랜치의 repository 메서드를 임시 Spring Boot test에서 실행한 뒤 Hibernate SQL 로그로 캡처한 actual SQL 기준으로 측정했다. 실제 SQL은 `LIMIT 10`이 아니라 `fetch first 10 rows only`로 렌더링된다.

구독 중인 관심사 조회는 `SubscribeRepository.findSubscribedInterestActivities()` JPQL을 임시 Spring Boot test에서 실행한 뒤 Hibernate SQL 로그로 캡처해 측정했다. `subscriptions.created_at` 컬럼과 `created_at DESC, id DESC` 정렬이 반영된 SQL 기준이다. API 조립 전 단계의 SQL baseline이므로 p95/p99, error rate는 이번 API baseline에서 별도로 기록한다.

Seed 실행 명령:

```powershell
$env:MONEW_DB_PORT='5435'
docker compose -p monew-perf-rerun --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=100k postgres-seed
docker compose -p monew-perf-rerun --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=1m postgres-seed
docker compose -p monew-perf-rerun --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=10m postgres-seed
```

## Seed 결과

| Seed scale | Seed 소요 시간 | DB size | users | interests | keywords | subscriptions | articles | comments | comment_likes | article_views | target recent rows per activity |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `3.254 s` | `36 MB` | `1,000` | `500` | `1,500` | `5,045` | `20,000` | `40,000` | `30,000` | `30,000` | `1,000` |
| `1m` | `15.047 s` | `283 MB` | `10,000` | `5,000` | `15,000` | `50,045` | `200,000` | `400,000` | `300,000` | `300,000` | `10,000` |
| `10m` | `129.118 s` | `2778 MB` | `100,000` | `50,000` | `150,000` | `500,045` | `2,000,000` | `4,000,000` | `3,000,000` | `3,000,000` | `10,000` |

주요 테이블 크기:

| Seed scale | articles | comments | comment_likes | article_views | users | subscriptions |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `8624 kB` | `5920 kB` | `4992 kB` | `7136 kB` | `304 kB` | `1048 kB` |
| `1m` | `85 MB` | `57 MB` | `48 MB` | `69 MB` | `2456 kB` | `9728 kB` |
| `10m` | `848 MB` | `595 MB` | `475 MB` | `700 MB` | `23 MB` | `95 MB` |

구독 관심사 조회 대상 데이터:

| Seed scale | target subscriptions | target keywords | DB size |
| --- | ---: | ---: | ---: |
| `100k` | `50` | `150` | `36 MB` |
| `1m` | `50` | `150` | `283 MB` |
| `10m` | `50` | `150` | `2778 MB` |

API baseline 직전 seed row count:

| seed scale | users | interests | subscriptions | articles | comments | comment_likes | article_views |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `1,000` | `500` | `5,045` | `20,000` | `40,000` | `30,000` | `30,000` |
| `1m` | `10,000` | `5,000` | `50,045` | `200,000` | `400,000` | `300,000` | `300,000` |
| `10m` | `100,000` | `50,000` | `500,045` | `2,000,000` | `4,000,000` | `3,000,000` | `3,000,000` |

## SQL 실행 시간 요약

| Seed scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 최근 작성 댓글 | `7.736 ms` | `8.953`, `8.983`, `11.553`, `9.165`, `8.712 ms` | `8.983 ms` |
| `100k` | 최근 좋아요한 댓글 | `8.936 ms` | `9.066`, `9.725`, `11.235`, `9.524`, `8.936 ms` | `9.524 ms` |
| `100k` | 최근 조회 기사 | `25.568 ms` | `25.981`, `27.132`, `27.095`, `27.150`, `26.654 ms` | `27.095 ms` |
| `100k` | 구독 중인 관심사 main | `5.517 ms` | `3.951`, `3.803`, `4.980`, `4.414`, `4.327 ms` | `4.327 ms` |
| `100k` | 구독 관심사 keywords | `0.099 ms` | `0.538`, `0.449`, `0.446`, `0.450`, `0.517 ms` | `0.450 ms` |
| `100k` | 구독 중인 관심사 total | `-` | `4.472`, `4.481`, `4.332`, `4.380`, `5.195 ms` | `4.472 ms` |
| `1m` | 최근 작성 댓글 | `12.836 ms` | `14.151`, `13.587`, `13.720`, `17.696`, `14.005 ms` | `14.005 ms` |
| `1m` | 최근 좋아요한 댓글 | `10.515 ms` | `12.238`, `11.750`, `12.216`, `11.807`, `11.632 ms` | `11.807 ms` |
| `1m` | 최근 조회 기사 | `235.410 ms` | `236.583`, `226.549`, `231.003`, `234.893`, `226.372 ms` | `231.003 ms` |
| `1m` | 구독 중인 관심사 main | `2.666 ms` | `2.961`, `2.884`, `2.772`, `2.762`, `2.634 ms` | `2.772 ms` |
| `1m` | 구독 관심사 keywords | `0.047 ms` | `0.451`, `0.464`, `0.572`, `0.585`, `0.292 ms` | `0.464 ms` |
| `1m` | 구독 중인 관심사 total | `-` | `4.708`, `4.280`, `3.549`, `3.453`, `3.331 ms` | `3.549 ms` |
| `10m` | 최근 작성 댓글 | `88.314 ms` | `80.545`, `82.747`, `95.963`, `93.401`, `78.940 ms` | `82.747 ms` |
| `10m` | 최근 좋아요한 댓글 | `44.102 ms` | `48.387`, `52.918`, `45.801`, `44.662`, `45.905 ms` | `45.905 ms` |
| `10m` | 최근 조회 기사 | `1820.813 ms` | `1825.932`, `1805.697`, `1861.183`, `1813.368`, `1832.137 ms` | `1825.932 ms` |
| `10m` | 구독 중인 관심사 main | `10.052 ms` | `11.051`, `10.541`, `10.535`, `10.878`, `10.810 ms` | `10.810 ms` |
| `10m` | 구독 관심사 keywords | `0.050 ms` | `0.517`, `0.391`, `0.496`, `0.596`, `0.486 ms` | `0.496 ms` |
| `10m` | 구독 중인 관심사 total | `-` | `11.635`, `11.394`, `12.454`, `14.413`, `11.481 ms` | `11.635 ms` |

## MID4-125 연결 판단

100k와 1m seed scale API baseline은 20 req/s에서 dropped iteration 없이 통과했다. 단, 1m seed scale은 p95가 `474.02 ms`까지 증가하고 PostgreSQL CPU가 `1020.92%`까지 관측되어 여유가 크다고 보기는 어렵다.

10m seed scale API baseline은 같은 조건에서 `requests=222`, `RPS=3.11`, `droppedIterations=979`, `p95=32353.24 ms`로 측정되어 20 req/s를 처리하지 못했다. error rate는 `0.00%`였으므로 응답 성공 여부가 아니라 처리량과 latency가 병목이다.

10m seed scale SQL baseline에서는 최근 조회 기사 쿼리가 `1825.932 ms` median으로 가장 큰 병목 후보이며, `article_views.user_id` 접근 경로와 `comments.article_id` 반복 scan을 먼저 RDB 인덱스 후보로 검증해야 한다.

따라서 MID4-125 판단에는 다음 순서로 연결한다.

1. MID4-133에서 RDB 인덱스 후보를 먼저 반영한다.
2. 같은 seed scale과 k6 조건으로 10m API baseline을 재측정한다.
3. RDB 최적화 이후에도 10m seed scale에서 p95/p99, dropped iterations, DB 부하가 목표를 넘으면 병목 조회를 MongoDB Read Model 후보로 올린다.
