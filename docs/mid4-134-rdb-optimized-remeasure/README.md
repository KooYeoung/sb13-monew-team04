# MID4-134 RDB optimized 재측정

## 요약

MID4-132 RDB baseline 이후 MID4-133 인덱스를 적용한 상태에서 단일 사용자 활동내역 API와 주요 SQL을 재측정했다. 이번 재측정은 132번과 동일하게 SQL 3회 warm-up, EXPLAIN (ANALYZE, BUFFERS), 5회 반복 실행 median 기록 방식을 적용했고, k6도 smoke와 baseline 결과 및 DB/Docker stats를 함께 남겼다.

## 상세 문서

| 구분 | 상세 문서 | optimized 결과 | 원자료 |
| --- | --- | ---: | --- |
| 단일 활동내역 API | [api-remeasure.md](api-remeasure.md) | 10m p95 19.25 ms, dropped 0 | [raw/rerun-132-method](raw/rerun-132-method) |
| 최근 작성 댓글 | [recent-comments.md](recent-comments.md) | 10m median 0.476 ms | [raw/rerun-132-method/sql-10m.out](raw/rerun-132-method/sql-10m.out) |
| 최근 좋아요한 댓글 | [recent-liked-comments.md](recent-liked-comments.md) | 10m median 0.684 ms | [raw/rerun-132-method/sql-10m.out](raw/rerun-132-method/sql-10m.out) |
| 최근 조회 기사 | [recent-article-views.md](recent-article-views.md) | 10m median 0.525 ms | [raw/rerun-132-method/sql-10m.out](raw/rerun-132-method/sql-10m.out) |
| 구독 중인 관심사 | [subscribed-interests.md](subscribed-interests.md) | 10m total median 1.283 ms | [raw/rerun-132-method/sql-10m.out](raw/rerun-132-method/sql-10m.out) |

## 측정 기준

- 대상 Jira: MID4-134
- Parent: MID4-77
- 선행 Jira: MID4-132, MID4-133
- 측정 branch: docs/MID4-134-rdb-optimized-remeasure
- 측정 commit: 39b5c7a
- 실행 시각: 2026-08-25 23:15-23:21 KST
- API: GET /api/user-activities/{userId}
- 대상 사용자 ID: 00000001-0000-4000-8000-000000000001
- DB project: monew-perf-134-rerun
- PostgreSQL container: monew-perf-134-rerun-postgres-1
- PostgreSQL port: 15434 -> 5432
- App profile/port: dev, 8080
- k6 source: scripts/performance/activity-history/k6/activity-history-baseline.js
- raw: [raw/rerun-132-method](raw/rerun-132-method)

## 측정 방법

각 seed scale마다 아래 순서로 진행했다.

1. postgres-seed로 데이터를 재생성한다.
2. seed 함수 말미의 ANALYZE로 optimizer 통계를 갱신한다.
3. k6 smoke로 API 응답을 확인한다.
4. baseline 직전 pg_stat_reset()을 실행한다.
5. k6 baseline을 20 rps, 1m, preAllocatedVUs=20, maxVUs=100 조건으로 실행한다.
6. baseline 중간에 Docker stats와 pg_stat_activity를 저장한다.
7. baseline 직후 pg_stat_database와 Docker stats를 저장한다.
8. 같은 scale에서 SQL별 3회 warm-up 후 EXPLAIN (ANALYZE, BUFFERS)와 5회 반복 실행 시간을 측정한다.

실행 스크립트:

- [raw/rerun-132-method/run-rerun-132-method.ps1](raw/rerun-132-method/run-rerun-132-method.ps1)
- [raw/rerun-132-method/sql-template.sql](raw/rerun-132-method/sql-template.sql)

## Seed Scale 정의

100k, 1m, 10m는 각 테이블 row 수가 아니라 seed_activity_history(scale_count)에 전달한 seed scale이다. 실제 row 수는 seed 함수의 도메인 분포에 따라 생성된다.

- articles: scale_count / 5
- comments: scale_count * 0.4
- comment_likes: scale_count * 0.3
- article_views: scale_count - comments - comment_likes
- target user의 최근 댓글, 좋아요, 조회 row 수: scale_count / 100, 최소 1,000, 최대 10,000

| seed scale | seed duration | DB size | users | interests | keywords | subscriptions | articles | comments | comment_likes | article_views |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100k | 3.650 s | 44 MB | 1,000 | 500 | 1,500 | 5,045 | 20,000 | 40,000 | 30,000 | 30,000 |
| 1m | 17.483 s | 367 MB | 10,000 | 5,000 | 15,000 | 50,045 | 200,000 | 400,000 | 300,000 | 300,000 |
| 10m | 148.312 s | 3655 MB | 100,000 | 50,000 | 150,000 | 500,045 | 2,000,000 | 4,000,000 | 3,000,000 | 3,000,000 |

## 인덱스 적용 확인

원문: [raw/rerun-132-method/applied-indexes.txt](raw/rerun-132-method/applied-indexes.txt)

| table | index | 목적 |
| --- | --- | --- |
| comments | idx_comments_user_created_id | 사용자 댓글 최신순 조회 |
| comments | idx_comments_article | 기사별 댓글 수 subquery |
| comment_likes | idx_comment_likes_liked_by_created_id | 사용자가 좋아요한 댓글 최신순 조회 |
| article_views | idx_article_views_user_viewed_id | 사용자가 조회한 기사 최신순 조회 |
| subscriptions | idx_subscriptions_user_created_id | 사용자가 구독 중인 관심사 최신순 조회 |

## API Optimized 요약

| seed scale | baseline p95 | optimized p95 | baseline p99 | optimized p99 | baseline RPS | optimized RPS | baseline dropped | optimized dropped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100k | 53.58 ms | 24.29 ms | 63.40 ms | 28.53 ms | 20.01 | 21.01 | 0 | 0 |
| 1m | 474.02 ms | 21.82 ms | 657.60 ms | 25.69 ms | 19.97 | 21.01 | 0 | 0 |
| 10m | 32353.24 ms | 19.25 ms | 43167.63 ms | 22.63 ms | 3.11 | 21.02 | 979 | 0 |

## SQL Median 요약

| 조회 | seed scale | baseline median | optimized median | delta | change |
| --- | --- | ---: | ---: | ---: | ---: |
| 최근 작성 댓글 | 100k | 8.983 ms | 0.431 ms | -8.552 ms | -95.20% |
| 최근 작성 댓글 | 1m | 14.005 ms | 0.423 ms | -13.582 ms | -96.98% |
| 최근 작성 댓글 | 10m | 82.747 ms | 0.476 ms | -82.271 ms | -99.42% |
| 최근 좋아요한 댓글 | 100k | 9.524 ms | 0.671 ms | -8.853 ms | -92.95% |
| 최근 좋아요한 댓글 | 1m | 11.807 ms | 0.804 ms | -11.003 ms | -93.19% |
| 최근 좋아요한 댓글 | 10m | 45.905 ms | 0.684 ms | -45.221 ms | -98.51% |
| 최근 조회 기사 | 100k | 27.095 ms | 0.645 ms | -26.450 ms | -97.62% |
| 최근 조회 기사 | 1m | 231.003 ms | 0.539 ms | -230.464 ms | -99.77% |
| 최근 조회 기사 | 10m | 1825.932 ms | 0.525 ms | -1825.407 ms | -99.97% |
| 구독 중인 관심사 total | 100k | 4.472 ms | 4.053 ms | -0.419 ms | -9.37% |
| 구독 중인 관심사 total | 1m | 3.549 ms | 1.505 ms | -2.044 ms | -57.59% |
| 구독 중인 관심사 total | 10m | 11.635 ms | 1.283 ms | -10.352 ms | -88.97% |

## MID4-125 연결 판단

MID4-133 인덱스 적용 후 MID4-134 API 재측정은 20 rps, 1m 조건에서 100k, 1m, 10m 모두 dropped iteration 0, error rate 0.00%로 완료됐다. 현재 측정 조건만으로는 활동내역 RDB 조회가 병목이라고 단정하기 어렵다.

구독 관심사 조회에서 100k scale의 일부 Seq Scan은 작은 테이블 크기 영향으로 볼 수 있다. 사용자 구독 접근 경로는 100k부터 idx_subscriptions_user_created_id를 사용했고, 1m/10m scale에서는 주요 접근 경로의 인덱스 사용과 실행 시간 개선이 확인된다.

따라서 MID4-125 MongoDB Read Model 적용 여부는 목표 처리량, p95/p99 SLO, 허용 error rate, dropped iteration 기준을 정한 뒤 판단한다. 현재 문서는 RDB 인덱스 최적화 후 같은 조건에서 재측정한 기준선으로 사용한다.
