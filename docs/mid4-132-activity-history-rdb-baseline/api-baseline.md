# 단일 사용자 활동내역 API baseline

## 측정 대상

- API: `GET /api/user-activities/{userId}`
- userId: `00000001-0000-4000-8000-000000000001`
- app source: 측정 당시 `origin/develop` `ffed6e1` 기준 MID4-132 브랜치
- current branch base: `origin/develop` `5bc6c4e` (PR #69, PR #71 반영)
- k6 source: `scripts/performance/activity-history/k6/activity-history-baseline.js` (PR #69 병합, 측정 당시 source `5a5d761`)
- k6 실행 방식: 측정 당시에는 PR #69 스크립트를 stdin으로 주입했고, 현재 develop에는 같은 k6 스크립트가 포함됨
- DB: `monew-perf-132-postgres-1`, PostgreSQL 16.14, port `5433 -> 5432`

## Seed Scale 정의

이 문서의 `100k`, `1m`, `10m`은 각 테이블 row 수가 아니라 `seed_activity_history(scale_count)`에 전달한 seed scale이다. 실제 row 수는 seed 함수에서 도메인 분포에 맞춰 파생되며, 아래 표에 측정 직전 row count를 함께 기록한다.

target user의 최근 댓글, 좋아요, 조회 row 수는 `scale_count / 100`이고 최소 `1,000`, 최대 `10,000`으로 제한된다. 따라서 `10m seed scale`은 각 테이블 1,000만 건이 아니라 `articles=2,000,000`, `comments=4,000,000`, `comment_likes=3,000,000`, `article_views=3,000,000`인 분포형 seed다.

| seed scale | seed 소요 시간 | DB size | users | interests | subscriptions | articles | comments | comment_likes | article_views |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `3.497 s` | `36 MB` | `1,000` | `500` | `5,045` | `20,000` | `40,000` | `30,000` | `30,000` |
| `1m` | `15.027 s` | `283 MB` | `10,000` | `5,000` | `50,045` | `200,000` | `400,000` | `300,000` | `300,000` |
| `10m` | `129.231 s` | `2778 MB` | `100,000` | `50,000` | `500,045` | `2,000,000` | `4,000,000` | `3,000,000` | `3,000,000` |

## Smoke 결과

smoke는 서버 기동 직후 단일 요청 검증이며, baseline 판단값으로 사용하지 않는다. `1m`, `10m` smoke는 HTTP 상태와 body check는 통과했지만 기본 duration threshold `p95 < 1000 ms`를 넘어서 k6 exit code는 실패였다.

| seed scale | requests | RPS | dropped iterations | error rate | duration avg | duration p95 | duration p99 | checks rate | threshold |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `100k` | `1` | `1.73` | `0` | `0.00%` | `573.67 ms` | `573.67 ms` | `573.67 ms` | `100.00%` | pass |
| `1m` | `1` | `0.45` | `0` | `0.00%` | `1206.74 ms` | `1206.74 ms` | `1206.74 ms` | `100.00%` | duration threshold fail |
| `10m` | `1` | `0.26` | `0` | `0.00%` | `2779.88 ms` | `2779.88 ms` | `2779.88 ms` | `100.00%` | duration threshold fail |

## Baseline 결과

실행 조건:

| setting | value |
| --- | --- |
| scenario | `baseline` |
| rate | `20` |
| timeUnit | `1s` |
| duration | `1m` |
| preAllocatedVUs | `20` |
| maxVUs | `100` |

결과:

| seed scale | summary path | requests | RPS | dropped iterations | error rate | duration avg | duration p95 | duration p99 | checks rate | threshold |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `100k` | `/results/activity-history-100k-baseline-summary.json` | `1201` | `20.01` | `0` | `0.00%` | `46.20 ms` | `53.58 ms` | `63.40 ms` | `100.00%` | pass |
| `1m` | `/results/activity-history-1m-baseline-summary.json` | `1201` | `19.97` | `0` | `0.00%` | `379.20 ms` | `474.02 ms` | `657.60 ms` | `100.00%` | pass |
| `10m` | `/results/activity-history-10m-baseline-summary.json` | `222` | `3.11` | `979` | `0.00%` | `28084.62 ms` | `32353.24 ms` | `43167.63 ms` | `100.00%` | duration/dropped threshold fail |

summary JSON:

```json
{
  "100k": {
    "scenario": "baseline",
    "url": "http://host.docker.internal:8080/api/user-activities/00000001-0000-4000-8000-000000000001",
    "targetUserId": "00000001-0000-4000-8000-000000000001",
    "expectedStatus": 200,
    "metrics": {
      "requests": 1201,
      "rps": 20.010966232098443,
      "droppedIterations": 0,
      "errorRate": 0,
      "durationAvgMs": 46.19865206661124,
      "durationP95Ms": 53.57953,
      "durationP99Ms": 63.400233,
      "checksRate": 1
    }
  },
  "1m": {
    "scenario": "baseline",
    "url": "http://host.docker.internal:8080/api/user-activities/00000001-0000-4000-8000-000000000001",
    "targetUserId": "00000001-0000-4000-8000-000000000001",
    "expectedStatus": 200,
    "metrics": {
      "requests": 1201,
      "rps": 19.974124263769777,
      "droppedIterations": 0,
      "errorRate": 0,
      "durationAvgMs": 379.2049571007492,
      "durationP95Ms": 474.020626,
      "durationP99Ms": 657.60041,
      "checksRate": 1
    }
  },
  "10m": {
    "scenario": "baseline",
    "url": "http://host.docker.internal:8080/api/user-activities/00000001-0000-4000-8000-000000000001",
    "targetUserId": "00000001-0000-4000-8000-000000000001",
    "expectedStatus": 200,
    "metrics": {
      "requests": 222,
      "rps": 3.1071861049580423,
      "droppedIterations": 979,
      "errorRate": 0,
      "durationAvgMs": 28084.618685743244,
      "durationP95Ms": 32353.24468075,
      "durationP99Ms": 43167.62937658995,
      "checksRate": 1
    }
  }
}
```

## DB 부하

baseline 직전 `pg_stat_reset()`을 실행해 통계를 분리했다.

중간 Docker stats:

| seed scale | CPU | MEM | NET I/O | BLOCK I/O | PIDS |
| --- | ---: | ---: | ---: | ---: | ---: |
| `100k` | `66.67%` | `78.62MiB / 30.91GiB` | `2.99MB / 17.7MB` | `52.7MB / 188MB` | `16` |
| `1m` | `1020.92%` | `425.5MiB / 30.91GiB` | `3.06MB / 18.4MB` | `235MB / 1.32GB` | `21` |
| `10m` | `939.03%` | `2.344GiB / 30.91GiB` | `4.74MB / 28.3MB` | `250MB / 14.3GB` | `23` |

중간 `pg_stat_activity`:

| seed scale | state | wait_event_type | count |
| --- | --- | --- | ---: |
| `100k` | active |  | `1` |
| `100k` | idle | `Client` | `10` |
| `1m` | active |  | `9` |
| `1m` | idle | `Client` | `2` |
| `1m` |  |  | `4` |
| `10m` | active |  | `12` |
| `10m` | active | `IO` | `1` |
| `10m` | active | `IPC` | `3` |

baseline 직후 `pg_stat_database`:

| seed scale | xact_commit | xact_rollback | blks_read | blks_hit | cache_hit_pct | tup_returned | tup_fetched | temp_files | temp_bytes | deadlocks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `6040` | `0` | `4` | `25,672,054` | `100.00` | `699,736,085` | `5,636,161` | `0` | `0` | `0` |
| `1m` | `20,517` | `0` | `971,241` | `89,681,578` | `98.93` | `6,068,642,963` | `2,964,713` | `0` | `0` | `0` |
| `10m` | `3,796` | `0` | `118,063,208` | `68,263,393` | `36.64` | `11,216,907,646` | `5,866,457` | `0` | `0` | `0` |

baseline 직후 Docker stats:

| seed scale | CPU | MEM | NET I/O | BLOCK I/O | PIDS |
| --- | ---: | ---: | ---: | ---: | ---: |
| `100k` | `0.17%` | `79.13MiB / 30.91GiB` | `4.52MB / 26.9MB` | `52.7MB / 206MB` | `16` |
| `1m` | `0.02%` | `395.1MiB / 30.91GiB` | `4.56MB / 27.7MB` | `236MB / 1.36GB` | `16` |
| `10m` | `0.00%` | `2.2GiB / 30.91GiB` | `5.55MB / 33.4MB` | `250MB / 14.4GB` | `16` |

## 요청 1건당 SQL

코드 경로 기준 요청 1건은 6개 SQL로 구성된다. `UserActivityServiceImpl`은 사용자 조회 후 최근 조회 기사, 최근 작성 댓글, 최근 좋아요한 댓글, 구독 관심사 조회를 순서대로 조립한다. 구독 관심사는 DTO 변환 중 `Interest.getKeywords()` batch load가 한 번 추가된다.

| 순서 | SQL | 주요 비용 |
| ---: | --- | --- |
| 1 | `users` PK 조회 | 사용자 존재 및 `deleted_at` 확인 |
| 2 | 최근 조회 기사 | `article_views`, `articles`, `users` join, 댓글 수 subquery, 조회수 subquery |
| 3 | 최근 작성 댓글 | `comments`, `users`, `articles` join, 좋아요 수 subquery |
| 4 | 최근 좋아요한 댓글 | `comment_likes`, `comments`, `users`, `articles` join, 좋아요 수 subquery |
| 5 | 구독 관심사 main | `subscriptions`, `interests`, `users` join, 관심사별 구독자 수 subquery |
| 6 | 구독 관심사 keywords | `keywords.interest_id = any (?)` batch 조회 |

Spring transaction 경계는 상위 활동내역 서비스 전체에 하나로 묶이지 않고, 하위 repository/service 호출별 read transaction으로 나뉜다.

## 병목 판단

100k와 1m seed scale API baseline은 20 req/s에서 dropped iteration 없이 통과했다. 단, 1m seed scale에서는 p95가 `474.02 ms`까지 증가하고 PostgreSQL CPU가 약 `1020.92%`까지 올라가므로 여유가 크다고 보기는 어렵다.

10m seed scale API baseline은 같은 조건에서 요청 스케줄을 따라가지 못했다. k6가 max VUs `100`에 도달했고 `droppedIterations=979`, `RPS=3.11`, `p95=32353.24 ms`로 측정됐다. error rate는 `0.00%`였으므로 응답 자체는 성공했지만, 처리량과 latency 기준으로는 RDB 직접 조회 baseline을 통과하지 못한 결과다.

SQL 단위 10m seed scale baseline에서는 최근 조회 기사가 `1825.932 ms` median으로 가장 크다. 병목 후보는 `article_views.user_id` 접근 경로 부재와 댓글 수 subquery의 `comments.article_id` 반복 scan이다. MongoDB Read Model 적용 판단 전 MID4-133에서 RDB 인덱스 후보를 먼저 검증해야 한다.
