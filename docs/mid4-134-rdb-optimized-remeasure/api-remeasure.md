# 단일 사용자 활동내역 API 인덱스 적용 후 결과

> [MID4-134 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 개선 전에는 가장 큰 테스트 데이터에서 초당 20건을 따라가지 못했지만, 인덱스 적용 후에는 요청 누락 없이 처리했다.
- 같은 조건의 p95는 `32,353.24 ms`에서 `19.25 ms`로 줄었다.
- DB CPU도 측정 중 `9.63%`로 관찰되어 개선 전보다 훨씬 안정적이었다.
- 이 문서의 수치는 인덱스 효과를 확인한 값이며 운영 환경의 보장값은 아니다.

## 무엇을 측정했나

- API: GET /api/user-activities/{userId}
- userId: 00000001-0000-4000-8000-000000000001
- app profile: dev
- app port: 8080
- DB project: monew-perf-134-rerun
- PostgreSQL container: monew-perf-134-rerun-postgres-1
- PostgreSQL port: 15434 -> 5432
- k6 source: scripts/performance/activity-history/k6/activity-history-baseline.js
- raw: [raw/rerun-132-method](raw/rerun-132-method)

## 테스트 데이터 크기

100k, 1m, 10m는 각 테이블 row 수가 아니라 seed_activity_history(scale_count)에 전달한 seed scale이다. 실제 row count는 seed 함수의 분포를 따른다.

| seed scale | seed duration | DB size | users | interests | keywords | subscriptions | articles | comments | comment_likes | article_views | target recent rows |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100k | 3.650 s | 44 MB | 1,000 | 500 | 1,500 | 5,045 | 20,000 | 40,000 | 30,000 | 30,000 | 1,000 |
| 1m | 17.483 s | 367 MB | 10,000 | 5,000 | 15,000 | 50,045 | 200,000 | 400,000 | 300,000 | 300,000 | 10,000 |
| 10m | 148.312 s | 3655 MB | 100,000 | 50,000 | 150,000 | 500,045 | 2,000,000 | 4,000,000 | 3,000,000 | 3,000,000 | 10,000 |

snapshot 원문:

- [snapshot-100k.txt](raw/rerun-132-method/snapshot-100k.txt)
- [snapshot-1m.txt](raw/rerun-132-method/snapshot-1m.txt)
- [snapshot-10m.txt](raw/rerun-132-method/snapshot-10m.txt)

## 준비 확인 결과(smoke)

smoke는 서버 기동 및 API 응답 검증용 단일 요청이며 baseline 판단값으로 사용하지 않는다.

| seed scale | requests | RPS | dropped iterations | error rate | duration avg | duration p95 | duration p99 | checks rate | threshold |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `100k` | `1` | `0.73` | `0` | `0.00`% | `367.76` ms | `367.76` ms | `367.76` ms | `100.00`% | pass |
| `1m` | `1` | `0.97` | `0` | `0.00`% | `23.02` ms | `23.02` ms | `23.02` ms | `100.00`% | pass |
| `10m` | `1` | `0.98` | `0` | `0.00`% | `21.09` ms | `21.09` ms | `21.09` ms | `100.00`% | pass |

## 인덱스 적용 후 결과

실행 조건:

| setting | value |
| --- | --- |
| scenario | baseline |
| rate | 20 |
| timeUnit | 1s |
| duration | 1m |
| preAllocatedVUs | 20 |
| maxVUs | 100 |

결과:

| seed scale | requests | RPS | dropped iterations | error rate | duration avg | duration p95 | duration p99 | checks rate | threshold |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `100k` | `1201` | `21.01` | `0` | `0.00`% | `19.49` ms | `24.29` ms | `28.53` ms | `100.00`% | pass |
| `1m` | `1201` | `21.01` | `0` | `0.00`% | `17.73` ms | `21.82` ms | `25.69` ms | `100.00`% | pass |
| `10m` | `1201` | `21.02` | `0` | `0.00`% | `16.34` ms | `19.25` ms | `22.63` ms | `100.00`% | pass |

summary JSON:

- [activity-history-100k-smoke-summary.json](raw/rerun-132-method/activity-history-100k-smoke-summary.json)
- [activity-history-1m-smoke-summary.json](raw/rerun-132-method/activity-history-1m-smoke-summary.json)
- [activity-history-10m-smoke-summary.json](raw/rerun-132-method/activity-history-10m-smoke-summary.json)
- [activity-history-100k-optimized-summary.json](raw/rerun-132-method/activity-history-100k-optimized-summary.json)
- [activity-history-1m-optimized-summary.json](raw/rerun-132-method/activity-history-1m-optimized-summary.json)
- [activity-history-10m-optimized-summary.json](raw/rerun-132-method/activity-history-10m-optimized-summary.json)

## 개선 전과 비교

| seed scale | baseline p95 | optimized p95 | p95 delta | p95 change | baseline p99 | optimized p99 | p99 delta | p99 change | baseline RPS | optimized RPS | baseline dropped | optimized dropped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100k | 53.58 ms | 24.29 ms | -29.286 ms | -54.66% | 63.40 ms | 28.53 ms | -34.869 ms | -55.00% | 20.01 | 21.01 | 0 | 0 |
| 1m | 474.02 ms | 21.82 ms | -452.204 ms | -95.40% | 657.60 ms | 25.69 ms | -631.908 ms | -96.09% | 19.97 | 21.01 | 0 | 0 |
| 10m | 32353.24 ms | 19.25 ms | -32333.998 ms | -99.94% | 43167.63 ms | 22.63 ms | -43145.003 ms | -99.95% | 3.11 | 21.02 | 979 | 0 |

## DB 부하

baseline 직전 pg_stat_reset()을 실행해 통계를 분리했다.

중간 Docker stats:

| seed scale | CPU | MEM | NET I/O | BLOCK I/O | PIDS |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100k | 10.89% | 137.8MiB / 30.91GiB | 2.38MB / 14.3MB | 18.7MB / 401MB | 16 |
| 1m | 6.73% | 296.3MiB / 30.91GiB | 6.61MB / 41.1MB | 18.7MB / 2.32GB | 16 |
| 10m | 9.63% | 2.329GiB / 30.91GiB | 10.8MB / 67.8MB | 93.6MB / 18.3GB | 16 |

중간 pg_stat_activity:

| seed scale | state | wait_event_type | wait_event | count |
| --- | --- | --- | --- | ---: |
| 100k | active |  |  | 1 |
| 100k | idle | Client | ClientRead | 10 |
| 1m | active |  |  | 1 |
| 1m | idle | Client | ClientRead | 10 |
| 10m | active |  |  | 1 |
| 10m | idle | Client | ClientRead | 10 |

baseline 직후 pg_stat_database:

| seed scale | xact_commit | xact_rollback | blks_read | blks_hit | cache_hit_pct | tup_returned | tup_fetched | temp_files | temp_bytes | deadlocks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100k | 5,933 | 0 | 3 | 2,628,465 | 100.00 | 62,686,693 | 1,117,700 | 0 | 0 | 0 |
| 1m | 5,934 | 0 | 88 | 4,090,783 | 100.00 | 1,988,451 | 1,987,461 | 0 | 0 | 0 |
| 10m | 5,931 | 0 | 114 | 4,909,284 | 100.00 | 1,955,135 | 1,954,140 | 0 | 0 | 0 |

baseline 직후 Docker stats:

| seed scale | CPU | MEM | NET I/O | BLOCK I/O | PIDS |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100k | 0.12% | 138.6MiB / 30.91GiB | 4.36MB / 26.9MB | 18.7MB / 406MB | 16 |
| 1m | 0.17% | 296.2MiB / 30.91GiB | 8.59MB / 53.6MB | 18.7MB / 2.32GB | 16 |
| 10m | 3.42% | 2.328GiB / 30.91GiB | 12.8MB / 80.4MB | 93.6MB / 18.3GB | 16 |

## 요청 1건당 SQL

코드 경로 기준 요청 1건은 6개 SQL로 구성된다. 이번 작업에서는 API method, repository method, JPQL, fetch 전략, 서비스 조립 로직을 변경하지 않았다.

| 순서 | SQL | 주요 비용 |
| ---: | --- | --- |
| 1 | 사용자 조회 | users PK 조회, deleted_at 확인 |
| 2 | 최근 조회 기사 | article_views, articles, users join, 댓글 수 subquery, 조회 수 subquery |
| 3 | 최근 작성 댓글 | comments, users, articles join, 좋아요 수 subquery |
| 4 | 최근 좋아요한 댓글 | comment_likes, comments, users, articles join, 좋아요 수 subquery |
| 5 | 구독 관심사 main | subscriptions, interests, users join, 관심사별 구독자 수 subquery |
| 6 | 구독 관심사 keywords | keywords.interest_id = any (?) batch 조회 |

## 병목 판단

MID4-133 인덱스 적용 후 100k, 1m, 10m 모두 20 rps, 1m 조건에서 dropped iteration 0, error rate 0.00%로 통과했다. 10m seed scale에서도 p95는 19.25 ms, p99는 22.63 ms다.

따라서 현재 측정 조건만으로는 활동내역 RDB 조회가 병목이라고 단정하기 어렵다. 병목 판단에는 목표 처리량, p95/p99 SLO, 허용 error rate, dropped iteration 허용 기준이 먼저 필요하다.
