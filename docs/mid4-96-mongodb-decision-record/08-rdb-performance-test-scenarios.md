# RDB 조회 성능 측정 시나리오

[상위 문서](./README.md) | [이전: RDB 테스트 데이터 생성 기준](./07-rdb-test-data-policy.md)

## 목적

이 문서는 단일 활동내역 API와 요청 내부 SQL/query를 RDB 기준으로 측정하기 위한 성능 테스트 시나리오를 정리한다.

MongoDB Read Model 적용 여부는 이 문서에서 결정하지 않는다. 이 문서의 결과 표와 병목 판단 기준을 바탕으로 `MID4-125`에서 MongoDB 적용 대상을 선정한다.

처음 읽는 사람은 [MID4-206 활동내역 조회 성능 테스트 결과](../mid4-206-mongodb-k6-compare.md)를 먼저 보면 된다. MID4-206 문서는 “무슨 테스트를 했고 결과가 무엇인지”를 설명하는 보고서이고, 이 문서는 “그 테스트를 어떤 조건과 SQL 기준으로 다시 실행할지”를 남기는 상세 기준 문서다.

## 읽는 방법

이 문서에서 쓰는 테스트 이름은 아래 의미로 읽는다.

| 문서의 이름 | 쉬운 의미 |
| --- | --- |
| Smoke | API가 정상 응답하는지 먼저 확인하는 시동 확인 테스트 |
| Baseline | 기본 상태에서 응답 시간이 어느 정도인지 보는 기준 테스트 |
| Average Load | 평소 수준의 동시 사용자를 가정한 일반 사용량 테스트 |
| High Load | 사용자가 많아진 상황을 가정한 높은 사용량 테스트 |
| Stress | 부하를 단계적으로 올려 어느 지점부터 느려지는지 보는 한계 테스트 |
| Throughput | 1초에 정해진 요청 수를 계속 보내며 처리량을 보는 테스트 |
| Soak | 같은 부하를 오래 유지해도 안정적인지 보는 장시간 유지 테스트 |
| Fan-out | 좋아요, 조회, 구독이 특정 글이나 관심사에 몰린 상황을 보는 데이터 몰림 테스트 |
| Exclusion | 삭제, 취소, 구독 해제 데이터를 걸러낼 때 비용을 보는 테스트 |
| Mixed | 조회 요청과 쓰기 요청이 함께 들어오는 상황을 보는 읽기/쓰기 혼합 테스트 |

`p95`, `p99`, `RPS`, `VU`, `dropped_iterations` 같은 측정 용어는 MID4-206 문서의 `읽기 전에 알아둘 말` 섹션을 따른다.

측정은 다음 순서로 진행한다.

```text
1. RDB 조회 기능 구현
2. 100k / 1m / 10m seed scale 데이터 준비
3. 현재 RDB 스키마와 기존 인덱스 기준으로 baseline 측정
4. 실행 계획, SQL 개수, full scan, 정렬 및 join 비용 확인
5. 병목이 확인된 조회에 한해 인덱스 후보 또는 쿼리 최적화 반영
6. 같은 데이터와 같은 k6 시나리오로 재측정
7. composite API p95/p99, error rate, SQL 개수, join 비용, DB 부하 비교
8. SQL/query별 측정값을 MongoDB 적용 후보 기능 선정 자료로 사용
```

새 인덱스는 측정 전에 선반영하지 않는다. PK/FK나 기존에 이미 존재하는 인덱스는 현재 RDB 기준에 포함하고, 추가 인덱스는 baseline 측정과 실행 계획 확인 이후 필요성이 확인될 때만 후보로 둔다.

## 측정 대상

측정 대상은 `MID4-123`에서 정의한 테스트 데이터 기준을 따른다.

| 기능 | 조회 기준 | 반환 기준 | 주요 비용 |
| --- | --- | --- | --- |
| 구독 중인 관심사 조회 | 사용자 ID 기준 현재 구독 중인 관심사 | 서버 응답 전체 반환 | 구독 상태 필터, 비노출 관심사 제외, 정렬 |
| 최근 작성 댓글 조회 | 사용자 ID 기준 작성 댓글 | 최신 10건 | 댓글 논리삭제 제외, 기사 삭제/비공개 제외, join |
| 최근 좋아요한 댓글 조회 | 사용자 ID 기준 좋아요한 댓글 | 최신 10건 | 좋아요 취소 제외, 댓글/기사 제외 조건, join |
| 최근 조회 기사 | 사용자 ID 기준 기사 조회 이력 | 최신 10건 | 삭제/비공개 기사 제외, 같은 기사 중복 제거, 정렬 |

아래 기능별 path 변수는 초기 설계 당시의 후보다. 실제 MID4-132, MID4-134, MID4-179 측정은 단일 활동내역 API인 `GET /api/user-activities/{userId}`와 단일 target user 기준으로 수행했다. 현재 제공된 API는 이 composite API 하나이므로, k6 RPS와 p95/p99는 활동 유형별로 분리해 산출하지 않는다.

```text
SUBSCRIBED_INTERESTS_PATH
RECENT_COMMENTS_PATH
LIKED_COMMENTS_PATH
VIEWED_ARTICLES_PATH
```

후속에 활동 유형별 endpoint가 추가되거나 query-level runtime 계측이 도입되기 전까지, 활동 유형별 후보 판단은 API RPS가 아니라 SQL/query별 실행 계획과 측정값을 사용한다.

## 데이터 규모별 측정 기준

성능 측정용 데이터는 아래 3단계를 사용한다.

| 규모 | 목적 | 판단 기준 |
| --- | --- | --- |
| 100k seed scale | 쿼리 구조와 현재 실행 계획 1차 확인 | SQL 개수, join 구조, full scan, 기본 p95 확인 |
| 1m seed scale | 일반적인 대량 데이터 상황 검증 | 평균 부하에서 p95/p99와 DB 부하 비교 |
| 10m seed scale | MongoDB Read Model 적용 필요성 판단 | RDB 최적화 이후에도 병목이 남는 기능 확인 |

각 규모에서는 같은 대표 사용자 그룹을 사용한다.

| 사용자 그룹 | 측정 목적 |
| --- | --- |
| 활동 없음 사용자 | 빈 목록 응답 비용 확인 |
| 평균 활동 사용자 | 일반 조회 비용 확인 |
| 활동 많은 사용자 | 다건 결과 조회와 정렬 비용 확인 |
| 제외 데이터 많은 사용자 | 논리삭제, 취소, 구독 해제 필터 비용 확인 |

## 인덱스 반영 기준

인덱스는 성능 측정 전에 바로 추가하지 않는다.

```text
현재 RDB 기준 baseline 측정
-> 실행 계획 확인
-> 병목 원인 확인
-> 인덱스 후보 또는 쿼리 최적화 도출
-> 반영 후 같은 조건으로 재측정
-> 개선 폭 기록
```

인덱스 후보는 다음 근거가 있을 때만 반영 대상으로 둔다.

```text
- full scan 또는 과도한 range scan이 확인되는 경우
- 최신순 정렬에서 filesort 또는 temp sort 비용이 큰 경우
- join 대상 row 수가 데이터 증가에 따라 크게 늘어나는 경우
- 논리삭제, 취소, 구독 해제 제외 조건 때문에 후보 row를 과도하게 읽는 경우
- 같은 endpoint가 반복 호출될 때 Postgres 컨테이너 CPU(docker stats) 또는 커넥션 대기 병목을 만드는 경우
```

인덱스를 추가한 경우에는 적용 전후를 같은 데이터 규모와 같은 k6 시나리오에서 비교한다.

## k6 시나리오

MID4-206 보강 시나리오에서는 단일 활동내역 API인 `GET /api/user-activities/{userId}`를 같은 부하 단계로 측정한다.

| 시나리오 | k6 값 | 부하 | 시간 | 목적 |
| --- | --- | --- | --- | --- |
| Smoke | `smoke` | 1~5 VU | 1분 | 인증, endpoint, 응답 검증 확인 |
| Baseline | `baseline` | 10~20 VU | 3~5분 | 기본 응답 시간과 SQL 비용 확인 |
| Average Load | `average` | 50 VU | 10분 | 일반 부하에서 p95/p99 확인 |
| High Load | `high-load` | 100 VU | 10분 | 병목 발생 여부 확인 |
| Stress | `stress` | 50 -> 100 -> 200 -> 400 VU | 각 단계 3~5분 | 성능이 무너지는 지점 확인 |

MID4-206 구현 기본값은 위 범위 안의 대표값으로 둔다. 기본값은 Smoke 1 VU 1분, Baseline 20 VU 5분, Average Load 50 VU 10분, High Load 100 VU 10분, Stress 각 단계 3분이다.

VU 기준 시나리오는 iteration 사이에 `K6_SLEEP_SECONDS`를 적용한다. 기본값은 1초이며, 최대 반복 부하를 확인할 때는 0으로 조정한다.

VU 기준 측정과 별도로 RPS 기준 측정을 필수 보강 시나리오로 수행한다.

RPS 기준 측정은 요청 스케줄을 따라가는지 확인하기 위한 시나리오이며, 목표 RPS와 `dropped_iterations=0`을 함께 성공 조건으로 본다.

```text
50 req/s
100 req/s
150 req/s
200 req/s
250 req/s
```

원인 분리나 경계 확인이 필요하면 300 req/s를 추가하고, 이때는 `preAllocatedVUs`와 `maxVUs`를 충분히 높여 k6 부하 생성기 부족과 서버 응답 지연을 분리한다.

RPS 기준 측정은 MID4-206의 `throughput` 시나리오로 실행하며, `K6_SLEEP_SECONDS`를 적용하지 않는다. 기존 MID4-179 결과에서 `baseline`이 RPS 측정 의미로 사용된 기록이 있으나, MID4-206 기준 `baseline`은 VU 기반 기본 비교이고 `throughput`이 RPS 기반 보강 측정이다.

RPS 기준 측정은 `GET /api/user-activities/{userId}` composite API에만 적용한다. 별도 endpoint 또는 query-level 계측이 없으면 구독 관심사, 최근 댓글, 좋아요 댓글, 조회 기사별 RPS와 p95/p99를 결과로 기록하지 않는다.

## 요청 구성

MID4-206에서 `K6_TARGET_USER_IDS`와 `K6_USER_PICK_STRATEGY`로 대표 사용자 ID 순환을 지원한다. 사용자 목록을 지정하지 않으면 `00000001-0000-4000-8000-000000000001` 단일 target user 기준으로 측정한다.

`K6_VARIANT=rdb|mongo`는 결과 산출물을 구분하기 위한 메타데이터다. 실제 RDB/MongoDB 전환은 애플리케이션 실행 설정 또는 배포 상태에서 맞춘 뒤 같은 k6 조건을 각각 실행한다.

```text
user-empty
user-interest
user-comment
user-like
user-article-view
user-mixed
user-deleted
```

composite API 응답에 포함되는 활동 유형별 검증은 다음 기준으로 구성한다.

| 기능 | 필수 검증 |
| --- | --- |
| 구독 중인 관심사 조회 | 구독 중인 관심사 전체 반환, 구독 해제/비노출 관심사 제외 |
| 최근 작성 댓글 조회 | 최신 10건 반환, 논리삭제 댓글 제외, 삭제/비공개 기사 댓글 제외 |
| 최근 좋아요한 댓글 조회 | 최신 10건 반환, 좋아요 취소 제외, 논리삭제 댓글 제외 |
| 최근 조회 기사 | 최신 10건 반환, 삭제/비공개 기사 제외, 같은 기사 중복 제거 |

응답 검증은 HTTP status와 기본 응답 구조까지만 k6에서 확인한다. 정렬과 제외 조건의 상세 정확성은 기능 테스트에서 먼저 검증한다.

## 측정 지표

k6와 애플리케이션, DB에서 아래 지표를 함께 수집한다.

| 구분 | 지표 |
| --- | --- |
| k6 | `http_req_duration p95`, `http_req_duration p99`, `http_req_failed`, RPS, `dropped_iterations` |
| 애플리케이션 | composite API response time, 요청 1건당 SQL 개수, 커넥션 풀 대기 |
| RDB | 쿼리 실행 시간, 실행 계획, 인덱스 사용 여부, Postgres 컨테이너 CPU(docker stats), slow query |
| 비교 판단 | composite API p95/p99 증가율, SQL/query별 join 비용, error rate |

임시 성공 기준은 다음과 같이 둔다.

아래 값은 초기 후보 판단 기준이며, MID4-179의 최종 처리량 판정 기준(`p95 < 1000ms`, `p99 < 2000ms`)과는 별도로 본다.

```text
http_req_failed < 1%
checks rate > 99%
dropped_iterations = 0
p95 < 200ms
p99 < 500ms
Postgres 컨테이너 CPU(docker stats) 지속 70% 미만
커넥션 풀 대기 거의 없음
```

Postgres 컨테이너 CPU(docker stats)는 k6 컨테이너 CPU를 합산한 값이 아니며, Docker CPU 표기 기준이라 100%를 초과할 수 있다. 결과 표의 CPU 값은 별도 표기가 없으면 실행 중 수집한 순간 스냅샷이며 평균, 최대, 지속 사용률이 아니다. 따라서 70% 기준은 지속 관측이 가능한 경우의 판단 기준이고, 표의 스냅샷 값이 70%를 넘는 경우에는 추가 확인이 필요한 신호로 해석한다.

프로젝트 환경에서 위 기준을 그대로 만족하기 어렵다면 composite API의 데이터 증가에 따른 악화 폭과 SQL/query별 상대 비용을 함께 본다.

## MID4-179 기준 측정 한계와 MID4-206 보강 범위

MID4-179에서 수행한 k6 측정은 MongoDB Read Model을 바로 적용하지 않아도 된다는 현재 판단의 참고 근거다. 다만 다음 한계가 있으므로 운영 환경의 최종 처리량 보장값으로 사용하지 않는다.

```text
- 로컬 dev 환경 기준 측정이다.
- 단일 target user 기준 측정이다.
- 각 요청량 단계가 1분으로 짧다.
- 같은 조건의 반복 측정으로 편차를 확인하지 않았다.
- multi-user 요청 분포를 검증하지 않았다.
- 구독 관심사 fan-out worst-case를 검증하지 않았다.
- 장시간 soak와 ramp-up/ramp-down 안정성을 검증하지 않았다.
- read/write 혼합 부하와 Outbox 처리 비용을 함께 검증하지 않았다.
- MongoDB Read Model 구현 전후 동일 조건 비교는 수행하지 않았다.
```

MID4-206에서는 위 한계 중 multi-user 요청 분포, VU 단계별 부하, RPS 보강 시나리오, 장시간 soak, 같은 조건 반복 측정, RDB/MongoDB 결과 구분용 메타데이터를 먼저 보강했다. 이후 같은 티켓에서 fan-out worst-case, 제외 조건 필터 비용, read/write 혼합 부하를 RDB 기준으로 추가 측정했다. MongoDB Read Model 구현 후 동일 조건 비교는 아직 별도 Jira 티켓에서 다룰 후속 범위다.

## 후속 테스트 공통 고정 조건

fan-out worst-case, 제외 조건 필터 비용, read/write 혼합 부하는 MID4-206 결과와 별도 결과 용도로 기록한다. 이 섹션의 고정 조건이 다른 결과끼리는 직접 비교하지 않는다.

| 항목 | 고정값 |
| --- | --- |
| 기준 DB | RDB PostgreSQL |
| 데이터 규모 | `SEED_SCALE=10m` |
| 애플리케이션 profile | `dev` |
| 로그 조건 | `dev-sql-warn` |
| SQL 로그 override | `org.hibernate.SQL=warn`, `org.hibernate.orm.jdbc.batch=warn`, `org.hibernate.orm.jdbc.bind=off` |
| 애플리케이션 로그 | dev profile 기본값 유지, `com.codeit.sb13.monew=debug` |
| k6 실행 위치 | Docker compose k6 |
| 기준 read API | `GET /api/user-activities/{userId}` |
| 기준 사용자 | `00000001-0000-4000-8000-000000000001` |
| 기본 사용자 전략 | `round-robin` |
| throughput 기본 VU | `preAllocatedVUs=500`, `maxVUs=500` |
| CPU 수집 | `StatsDelaySeconds=30` 기준 `docker stats --no-stream` |
| CPU 해석 | k6 컨테이너 CPU를 제외한 Postgres 컨테이너 순간 스냅샷 |
| read 성공 기준 | `http_req_failed < 1%`, `checks rate > 99%`, `dropped_iterations = 0`, `p95 < 200ms`, `p99 < 500ms` |
| 결과 비교 기준 | 로그 조건, 데이터, 사용자 수, VU/RPS, duration, 실행 순서가 모두 같을 때만 직접 비교 |

로그 조건을 `dev-sql-warn`으로 고정하는 이유는 MID4-206에서 확인한 SQL DEBUG/TRACE 로그 출력 영향 때문이다. `dev-default-debug`는 SQL/query 확인용으로만 사용하고, 성능 결과에는 `debug-reference`로 기록한다. `dev-sql-warn`은 SQL/배치 로그만 낮춘 dev 기준 측정이며, prod 수준의 무로그 성능 측정은 아니다. 후속 테스트의 최종 판단값은 `dev-sql-warn` 조건에서 수집한 결과만 사용한다. 상세 기준은 [MID4-206 MongoDB 적용 대비 k6 비교 테스트 보강](../mid4-206-mongodb-k6-compare.md#로그-조건-분기-이유)을 따른다.

각 테스트 실행 전에는 10m seed를 새로 적재한다. fan-out worst-case는 10m seed 적재 후 fan-out overlay를 적용하고 `ANALYZE`를 실행한 상태에서 측정한다. 제외 조건 필터 비용은 10m seed 적재 후 `exclusion-overlay.sql`을 적용하고, fan-out overlay와 동시에 적용하지 않는다. read/write 혼합 부하는 실행 중 DB 상태가 변하므로 시나리오 또는 반복 실행 단위마다 10m seed를 다시 적재한 결과만 직접 비교한다.

## 데이터 몰림 조회 테스트 (fan-out worst-case)

목적은 전체 테이블 규모가 아니라 특정 댓글, 기사, 관심사에 관계 데이터가 집중될 때 카운트 서브쿼리와 `users.deleted_at` 확인 비용이 커지는지 검증하는 것이다. 100 rps 이상 고부하 확인 결과는 `rdb-fanout-read`로 기록하고, 고부하가 모두 실패한 뒤 같은 조건에서 낮은 RPS 경계를 찾는 결과는 `rdb-fanout-boundary-reference`로 분리한다.

1차 fan-out overlay는 `scripts/performance/activity-history/fanout-overlay.sql`로 적용한다. 이 조건에는 극단 편중 관심사 1개 케이스를 섞지 않는다.

| 대상 | 고정 집중 조건 |
| --- | --- |
| 최근 댓글 10개 | 댓글당 좋아요 1,000개 |
| 최근 조회 기사 10개 | 기사당 댓글 1,000개 |
| 최근 조회 기사 10개 | 기사당 조회 사용자 10,000명 |
| 구독 관심사 50개 | 관심사당 구독자 1,000명 |

편중 관심사 1개 케이스는 `scripts/performance/activity-history/fanout-extreme-overlay.sql`로 별도 적용한다. 이 결과는 1차 fan-out과 직접 비교하지 않고 `rdb-fanout-extreme-reference`로만 기록한다.

| 대상 | 고정 집중 조건 | 결과 용도 |
| --- | --- | --- |
| 편중 관심사 1개 | 기존 구독자 외 구독자 50,000명 추가 | `rdb-fanout-extreme-reference` |

관계 데이터에 사용하는 사용자는 서로 다른 사용자로 구성한다. 동일 사용자를 반복 사용하면 PostgreSQL `Memoize`가 사용자 PK 조회 결과를 재사용해 `users.deleted_at` 반복 조회 위험을 작게 측정할 수 있다.

실행 순서는 `seed-common.sql`, `seed-10m.sql`, `fanout-overlay.sql`, `fanout-explain.sql`, k6 측정 순서로 고정한다. 극단 편중 reference는 같은 10m seed 기준에서 `fanout-extreme-overlay.sql` 적용 여부를 결과 표에 명시한 뒤 별도 실행한다.

| 단계 | 시나리오 | 부하 | 시간 | 목적 |
| --- | --- | --- | --- | --- |
| 1 | smoke | 1 VU | 1분 | overlay 데이터와 응답 구조 확인 |
| 2 | throughput | 100/150/200 rps | 각 1분 | 기존 RDB 안정 구간과 fan-out 조건 차이 확인 |
| 3 | throughput-boundary | 10/20/30/40/50 rps | 각 1분 | 고부하 실패 후 낮은 RPS 경계 탐색 |
| 4 | throughput-boundary | 60/70/80/90 rps | 각 1분 | 50 rps가 통과한 경우에만 추가 경계 탐색 |
| 5 | throughput-soak | boundary에서 성공 기준을 모두 만족한 가장 높은 RPS | 10분 | 단기 통과값의 유지 여부 확인 |

100/150/200 rps가 모두 실패하면 기존 고부하 soak는 실행하지 않는다. 대신 같은 고정 조건에서 10/20/30/40/50 rps lower-bound ladder를 실행하고, 50 rps가 통과할 때만 60/70/80/90 rps를 추가한다. 최고 통과 RPS는 `rdb-fanout-boundary-reference`로 10분 soak를 1회 실행한다. 모든 lower-bound ladder가 실패하면 `capacity < 10 rps`로 기록한다. 필수 확인 항목은 API p95/p99, `pk_users` index scan loops, 카운트 서브쿼리별 실행 시간, `shared hit`, `shared read`, Postgres 컨테이너 CPU(docker stats), 커넥션 대기다.

## 읽기/쓰기 혼합 부하 테스트 (RDB read/write mixed)

목적은 RDB 기준에서 쓰기 요청이 섞였을 때 활동내역 read 성능이 얼마나 흔들리는지 확인하는 것이다. 현재 Outbox 구현은 없으므로 결과 용도는 `rdb-mixed-no-outbox`로 기록한다. Outbox 구현 후 같은 조건으로 재측정할 때만 `rdb-mixed-with-outbox`와 비교한다.

| API 태그 | 비율 80/20 | 비율 50/50 | 요청 |
| --- | ---: | ---: | --- |
| `activity-history-read` | 80% | 50% | `GET /api/user-activities/{userId}` |
| `comment-create` | 5% | 10% | `POST /api/comments` |
| `comment-like-toggle` | 5% | 15% | `POST`와 `DELETE /api/comments/{commentId}/comment-likes`를 1:1로 교대 실행 |
| `article-view` | 5% | 15% | `POST /api/articles/{articleId}/article-views` |
| `subscription-toggle` | 5% | 10% | `POST`와 `DELETE /api/interests/{interestId}/subscriptions`를 1:1로 교대 실행 |

write 요청은 테스트 전용 사용자와 테스트 전용 기사, 댓글, 관심사 ID pool을 사용한다. 멱등 API라도 중복 요청 때문에 error rate가 왜곡되지 않도록 `comment-like-toggle`과 `subscription-toggle`은 같은 VU 안에서 같은 target을 `POST -> DELETE` 순서로 교대 실행한다.

`throughput`의 부하는 k6 `constant-arrival-rate` iteration 도착률이다. `comment-like-toggle`과 `subscription-toggle`은 한 iteration에서 `POST`와 `DELETE` 두 HTTP 요청을 실행하므로, 결과 표의 전체 HTTP RPS는 requested rate보다 높을 수 있다.

| 단계 | read/write 비율 | 시나리오 | 부하 | 시간 | 목적 |
| --- | --- | --- | --- | --- | --- |
| 1 | 80/20 | smoke | 5 VU | 1분 | 인증, payload, 응답 검증 |
| 2 | 80/20 | average | 50 VU | 10분 | 일반 혼합 부하 확인 |
| 3 | 80/20 | throughput | 100 rps | 1분 | 요청 도착률 기준 확인 |
| 4 | 80/20 | throughput | 150 rps | 1분 | read 안정 구간에서 write 영향 확인 |
| 5 | 50/50 | throughput-reference | 100 rps | 1분 | write 비중 증가 참고값 확보 |

결과는 전체 p95/p99만 기록하지 않는다. `api` tag 기준으로 `activity-history-read`, `comment-create`, `comment-like-toggle`, `article-view`, `subscription-toggle`의 p95/p99, error rate, checks rate를 분리 기록한다.

## 제외 조건 필터 비용 측정

논리삭제 사용자, 논리삭제 기사, 논리삭제 댓글이 많은 데이터에서는 제외 조건 필터 비용을 별도로 측정한다.

비교는 같은 데이터 규모에서 아래 두 그룹으로 나눠 진행한다.

| 그룹 | 데이터 특징 | 목적 |
| --- | --- | --- |
| 일반 사용자 그룹 | 제외 데이터 비율이 낮음 | 기본 조회 비용 측정 |
| 제외 데이터 많은 사용자 그룹 | 논리삭제, 취소, 구독 해제 데이터 비율이 높음 | 필터 조건과 join 비용 측정 |

확인 항목은 다음과 같다.

```text
- 제외 조건 때문에 scan 범위가 커지는지
- 최신 10건을 찾기 위해 읽는 후보 row 수가 늘어나는지
- 논리삭제 사용자 조건이 각 조회에 일관되게 적용되는지
- 기사 삭제/비공개 조건 때문에 댓글 조회와 좋아요 댓글 조회의 join 비용이 증가하는지
- 좋아요 취소와 구독 해제 조건이 인덱스를 타는지
```

물리삭제는 RDB 원본 제거와 MongoDB Read Model cleanup 기준 검증에 가깝기 때문에, RDB 조회 성능 측정에서는 논리삭제와 취소/해제 필터 비용을 우선 본다.

## 후속 테스트 결과 기록 표

fan-out worst-case 실행 결과는 아래 표에 추가한다.

| 결과 용도 | seed 조건 | 시나리오 | 부하 | 시간 | p95 | p99 | error rate | checks rate | dropped iterations | `pk_users` loops | Postgres CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `rdb-fanout-read` | 10m + fan-out overlay | smoke | 1 VU | 1분 | 187.53ms | 404.95ms | 0.00% | 100.00% | 0 | 최근 댓글 좋아요 10,010 / 최근 기사 댓글 10,020 / 최근 기사 조회 100,030 / 구독 관심사 50,545 | 0.00%(after) | after active 1, idle 11 | 워밍업 후 통과 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | smoke | 1 VU | 1분 | 158.94ms | 168.43ms | 0.00% | 100.00% | 0 | 최근 댓글 좋아요 10,010 / 최근 기사 댓글 10,020 / 최근 기사 조회 100,030 / 구독 관심사 50,545 | 0.04%(after) | after active 1, idle 10 | lower-bound 측정 전 smoke 통과 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | throughput | 10 rps | 1분 | 165.68ms | 196.45ms | 0.00% | 100.00% | 0 | 동일 | 129.29% | active 3, idle 8 | 통과 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | throughput | 20 rps | 1분 | 228.66ms | 281.29ms | 0.00% | 100.00% | 0 | 동일 | 303.35% | active 4, idle 7 | 실패, p95 기준 초과 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | throughput | 30 rps | 1분 | 11,951.05ms | 12,270.91ms | 0.00% | 100.00% | 0 | 동일 | 917.09% | active 11 | 실패, latency 급증 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | throughput | 40 rps | 1분 | 19,662.55ms | 20,367.61ms | 0.00% | 100.00% | 468 | 동일 | 905.92% | active 10, idle in transaction 1 | 실패, VU 포화와 dropped 발생 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | throughput | 50 rps | 1분 | 21,448.99ms | 22,445.07ms | 0.00% | 100.00% | 1,161 | 동일 | 920.58% | active 11 | 실패, VU 포화와 dropped 발생 |
| `rdb-fanout-boundary-reference` | 10m + fan-out overlay | throughput-soak | 10 rps | 10분 | 164.37ms | 187.57ms | 0.00% | 100.00% | 0 | 동일 | 131.29% | active 3, idle 8 | 통과, 지속 가능 기준 |
| `rdb-fanout-read` | 10m + fan-out overlay | throughput | 100 rps | 1분 | 21,531.50ms | 22,825.07ms | 0.00% | 100.00% | 4,097 | 동일 | 908.29% | active 10, idle in transaction 1 | 실패 |
| `rdb-fanout-read` | 10m + fan-out overlay | throughput | 150 rps | 1분 | 21,823.29ms | 22,448.29ms | 0.00% | 100.00% | 7,091 | 동일 | 900.05% | active 10, idle 1 | 실패 |
| `rdb-fanout-read` | 10m + fan-out overlay | throughput | 200 rps | 1분 | 23,169.63ms | 24,358.33ms | 0.00% | 100.00% | 10,124 | 동일 | 905.44% | active 11 | 실패 |
| `rdb-fanout-extreme-reference` | 10m + fan-out overlay + fan-out extreme overlay | smoke | 1 VU | 1분 | 181.06ms | 182.04ms | 0.00% | 100.00% | 0 | 구독 관심사 100,545, 편중 대상 활성 구독자 51,008 | 0.00%(after) | after active 1, idle 10 | 통과, 별도 reference |

seed 직후 첫 smoke는 p95 178.76ms, p99 647.35ms로 p99 기준을 초과했으므로 warmup-anomaly로만 보관한다. 두 번째 smoke는 같은 조건에서 통과했다. 초기 100/150/200 rps는 모두 실패했으므로 기존 고부하 soak는 실행하지 않았다. 이후 새 10m seed와 같은 fan-out overlay 조건으로 lower-bound를 재측정했으며, 10 rps 1분과 10 rps 10분 soak만 통과했다. 20 rps는 p95 기준을 초과했고, 30 rps부터 latency가 급증했으며, 40/50 rps는 VU 포화와 dropped iterations가 함께 발생했다. `rdb-fanout-extreme-reference`는 구독자 편중이 실행계획에 미치는 영향만 보기 위해 smoke와 EXPLAIN까지만 실행하고, 1차 fan-out과 직접 비교하지 않는다.

제외 조건 필터 비용 실행 결과는 아래 표에 추가한다.

| 결과 용도 | seed 조건 | 시나리오 | 부하 | 시간 | p95 | p99 | error rate | checks rate | dropped iterations | EXPLAIN 핵심값 | Postgres CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | smoke | 1 VU | 1분 | 126.29ms | 493.31ms | 0.00% | 100.00% | 0 | 최근 삭제 댓글 10,000 / 좋아요 대상 삭제 댓글 10,000 / 조회 대상 삭제 기사 10,000 / 삭제 subscriber 50,000 | N/A | 미계측 | 통과, p99 기준 근접 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | smoke | 1 VU | 1분 | 110.38ms | 112.87ms | 0.00% | 100.00% | 0 | 동일 | N/A | 미계측 | 통과, throughput 진입 기준 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | throughput | 10 rps | 1분 | 107.89ms | 123.09ms | 0.00% | 100.00% | 0 | 동일 | 76.12% | active 2, idle 9 | 통과 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | throughput | 20 rps | 1분 | 119.60ms | 146.44ms | 0.00% | 100.00% | 0 | 동일 | 154.97% | active 3, idle 8 | 통과 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | throughput | 30 rps | 1분 | 134.35ms | 163.87ms | 0.00% | 100.00% | 0 | 동일 | 249.81% | active 5, idle 6 | 통과 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | throughput | 40 rps | 1분 | 142.15ms | 182.72ms | 0.00% | 100.00% | 0 | 동일 | 336.70% | active 5, idle 5, idle in transaction 1 | 통과 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | throughput | 50 rps | 1분 | 188.02ms | 223.08ms | 0.00% | 100.00% | 0 | 동일 | 484.12% | active 7, idle 4 | 통과, 단기 기준 최고 통과 |
| `rdb-exclusion-filter-reference` | 10m + exclusion overlay | throughput-soak | 50 rps | 10분 | 228.00ms | 279.56ms | 0.00% | 100.00% | 0 | 동일 | 492.74% | active 7, idle 4 | 실패, p95 기준 초과 |

EXPLAIN 기준으로 최근 작성 댓글은 삭제 댓글 후보 10,000건 필터 후 1.560ms, 최근 좋아요 댓글은 `pk_comments` 10,010회 확인 후 8.944ms, 최근 조회 기사는 `pk_articles` 10,010회 확인 후 11.834ms, 구독 관심사는 `pk_users subscriber` 50,545회 확인 후 68.116ms로 관찰됐다. 삭제 후보 row 조건은 fan-out 조건보다 RPS 저하는 작았지만, 50 rps 10분 유지에서는 p95 기준을 초과했다.

read/write 혼합 부하 실행 결과는 아래 표에 추가한다.

| 결과 용도 | mix ratio | 시나리오 | 부하 | 시간 | api tag | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `rdb-mixed-no-outbox` | 80/20 | smoke | 5 VU | 1분 | overall | 32.61ms | 54.21ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | smoke | 5 VU | 1분 | `activity-history-read` | 28.00ms | 50.85ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | smoke | 5 VU | 1분 | `comment-create` | 167.00ms | 167.00ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | smoke | 5 VU | 1분 | `comment-like-toggle` | 36.75ms | 48.50ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | smoke | 5 VU | 1분 | `article-view` | 50.70ms | 62.14ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | smoke | 5 VU | 1분 | `subscription-toggle` | 29.50ms | 39.68ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | average | 50 VU | 10분 | overall | 30.55ms | 52.60ms | 0.01% | 99.99% | 0 | 23.87% | active 2, idle 10 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | average | 50 VU | 10분 | `activity-history-read` | 31.00ms | 49.00ms | 0.00% | 100.00% | 0 | 23.87% | active 2, idle 10 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | average | 50 VU | 10분 | `comment-create` | 25.00ms | 53.24ms | 0.00% | 100.00% | 0 | 23.87% | active 2, idle 10 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | average | 50 VU | 10분 | `comment-like-toggle` | 35.00ms | 74.41ms | 0.03% | 99.97% | 0 | 23.87% | active 2, idle 10 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | average | 50 VU | 10분 | `article-view` | 37.85ms | 63.77ms | 0.07% | 99.93% | 0 | 23.87% | active 2, idle 10 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | average | 50 VU | 10분 | `subscription-toggle` | 24.00ms | 56.00ms | 0.00% | 100.00% | 0 | 23.87% | active 2, idle 10 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 100 iter/s | 1분 | overall | 23.59ms | 30.03ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과, HTTP RPS 118.03 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 100 iter/s | 1분 | `activity-history-read` | 24.00ms | 30.00ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 100 iter/s | 1분 | `comment-create` | 14.00ms | 41.64ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 100 iter/s | 1분 | `comment-like-toggle` | 24.00ms | 33.00ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 100 iter/s | 1분 | `article-view` | 25.00ms | 34.18ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 100 iter/s | 1분 | `subscription-toggle` | 18.00ms | 22.00ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 150 iter/s | 1분 | overall | 22.09ms | 26.58ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과, HTTP RPS 175.87 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 150 iter/s | 1분 | `activity-history-read` | 23.00ms | 29.00ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 150 iter/s | 1분 | `comment-create` | 13.00ms | 17.38ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 150 iter/s | 1분 | `comment-like-toggle` | 23.00ms | 28.63ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 150 iter/s | 1분 | `article-view` | 23.00ms | 26.81ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 80/20 | throughput | 150 iter/s | 1분 | `subscription-toggle` | 16.00ms | 20.79ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 50/50 | throughput-reference | 100 iter/s | 1분 | overall | 19.95ms | 23.84ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과, HTTP RPS 133.53 |
| `rdb-mixed-no-outbox` | 50/50 | throughput-reference | 100 iter/s | 1분 | `activity-history-read` | 22.00ms | 26.00ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 50/50 | throughput-reference | 100 iter/s | 1분 | `comment-create` | 12.00ms | 15.00ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 50/50 | throughput-reference | 100 iter/s | 1분 | `comment-like-toggle` | 21.00ms | 25.00ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 50/50 | throughput-reference | 100 iter/s | 1분 | `article-view` | 21.00ms | 26.00ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과 |
| `rdb-mixed-no-outbox` | 50/50 | throughput-reference | 100 iter/s | 1분 | `subscription-toggle` | 15.05ms | 18.00ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과 |

공식 mixed 측정은 80/20 smoke 5 VU 1분, 80/20 average 50 VU 10분, 80/20 throughput 100/150 iter/s 1분, 50/50 throughput 100 iter/s 1분이다. smoke 이후 DB 상태에서 이어서 실행한 80/20 average 초기 검증값은 공식 비교 표에서 제외한다.

## 후속 테스트 변경 관리 기준

고정 조건이 바뀐 결과는 기존 결과와 직접 비교하지 않는다. `preAllocatedVUs`, `maxVUs`, duration, 사용자 수, 로그 레벨, seed overlay 조건, read/write 비율 중 하나라도 다르면 별도 결과 용도를 부여한다.

| 결과 용도 | 사용 조건 |
| --- | --- |
| `rdb-fanout-read` | RDB, 10m seed, `fanout-overlay.sql`, read-only 측정 |
| `rdb-fanout-boundary-reference` | RDB, 10m seed, `fanout-overlay.sql`, read-only lower-bound 경계 측정 |
| `rdb-fanout-extreme-reference` | RDB, 10m seed, `fanout-extreme-overlay.sql`, 편중 관심사 1개 reference 측정 |
| `rdb-exclusion-filter-reference` | RDB, 10m seed, `exclusion-overlay.sql`, read-only 제외 조건 필터 비용 측정 |
| `rdb-mixed-no-outbox` | RDB, 10m seed, Outbox 미구현, read/write 혼합 측정 |
| `rdb-mixed-with-outbox` | RDB, 10m seed, Outbox 구현 후 read/write 혼합 재측정 |
| `mongo-fanout-read` | MongoDB Read Model 구현 후 fan-out overlay와 같은 사용자 조건으로 read-only 측정 |

실행 조건을 바꿔 원인을 분리할 때는 `reference` 결과로만 기록한다. 예를 들어 `preAllocatedVUs=1000`, `maxVUs=1000`으로 바꾼 결과는 VU 한도 확인용이며, `preAllocatedVUs=500`, `maxVUs=500` 기준 결과와 직접 비교하지 않는다.

## SQL 및 join 확인 기준

요청 1건당 SQL 개수는 composite API 기준으로 기록하고, 병목 후보는 SQL/query별로 분리해 확인한다.

```text
API 요청 1건
-> 실행 SQL 개수
-> 가장 오래 걸린 SQL
-> join 대상 테이블
-> 인덱스 사용 여부
-> full scan 여부
```

N+1이 발생하면 MongoDB 적용 여부를 판단하기 전에 RDB 구현 또는 fetch 전략을 먼저 수정한다.

MongoDB 적용 검토는 RDB 쿼리와 인덱스 최적화를 반영한 뒤에도 병목이 남는 경우에만 진행한다.

## 결과 기록 표

성능 측정 결과는 측정 단계, 데이터 규모, 시나리오별로 분리해 기록한다. API p95/p99와 RPS는 `GET /api/user-activities/{userId}` composite API 기준으로만 기록한다.

MID4-206에서 2026-08-27~28 기준 10m seed scale 데이터를 새로 적재하고 smoke 워밍업을 포함해 RDB 기준 k6 측정을 완료했다. 추가로 160~190 rps 경계값, 주요 시나리오 총 5회 반복, 100/150/190 rps 30분 soak, 190 rps 5분 경계, 200/250/300 rps 원인 분리 측정을 수행했다. 이후 `dev-sql-warn` 조건에서 200/250/300 rps 동일 조건 3회 반복, 250/300 rps 10분 soak, stress 재측정, 5명 round-robin 200/250/300 rps, fan-out lower-bound 10~50 rps와 10 rps 10분 soak, 제외 조건 필터 비용, read/write mixed 부하 측정을 추가 수행했다. 상세 실행 로그와 결과 용도 구분 기준은 [MID4-206 MongoDB 적용 대비 k6 비교 테스트 보강](../mid4-206-mongodb-k6-compare.md)에 기록한다.

`dev-default-debug`는 `application-dev.yaml` 기본 로그 조건이며 `org.hibernate.SQL=debug`, `org.hibernate.orm.jdbc.batch=trace`가 켜진 상태다. `dev-sql-warn`은 같은 dev profile에서 SQL/배치 로그를 `warn`으로 낮춘 상태이며, 애플리케이션 패키지 DEBUG 로그는 유지된다. 별도 override 기록이 없는 초기 로컬 측정은 `dev-default-debug` 참고값으로 보고, 최종 경계 판단은 `dev-sql-warn` 재측정값을 우선한다. 로그 조건 분기 이유는 MID4-206 문서의 `로그 조건 분기 이유` 섹션을 따른다.

성능 결과는 `로그 조건`, `사용자 조건`, `preAllocatedVUs`, `maxVUs`, `시나리오`, `duration`, `실행 순서/워밍업 상태`가 같은 경우에만 직접 비교한다. 조건이 2개 이상 다른 결과끼리는 원인 판단에 사용하지 않는다. 결과 용도 구분값은 MID4-206 문서의 정의를 따른다.

| 측정 단계 | 데이터 규모 | 시나리오 | 로그 조건 | 사용자/VU 조건 | 결과 용도 | API | p95 | p99 | error rate | RPS | dropped iterations | 요청당 SQL | 주요 join | Postgres 컨테이너 CPU(docker stats) | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 현재 RDB baseline | 10m seed scale | Smoke, 1 VU, 워밍업 | dev-default-debug | 단일(1명, round-robin)/VU 기준 | debug-reference | GET /api/user-activities/{userId} | 39.99ms | 47.71ms | 0.00% | 0.97 | 0 | 미계측 | composite API 내부 조회 | 0.08% | active 1, idle 11 | 응답 검증 통과 |
| 현재 RDB baseline | 10m seed scale | Baseline, 20 VU, 5분 | dev-default-debug | 단일(1명, round-robin)/VU 기준 | debug-reference | GET /api/user-activities/{userId} | 26.84ms | 48.05ms | 0.00% | 19.50 | 0 | 미계측 | composite API 내부 조회 | 5.15% | active 1, idle 11 | 참고 통과 |
| 현재 RDB baseline | 10m seed scale | Average, 50 VU, 10분 | dev-default-debug | 단일(1명, round-robin)/VU 기준 | debug-reference | GET /api/user-activities/{userId} | 38.37ms | 81.65ms | 0.00% | 48.59 | 0 | 미계측 | composite API 내부 조회 | 16.60% | active 1, idle 11 | 참고 통과 |
| 현재 RDB baseline | 10m seed scale | Average, 50 VU, 10분, 5명 round-robin | dev-sql-warn | 5명 round-robin/VU 기준 | multi-user-reference | GET /api/user-activities/{userId} | 31.19ms | 47.88ms | 0.00% | 49.16 | 0 | 미계측 | composite API 내부 조회 | 13.25% | active 1, idle 11 | 다중 사용자 재측정 통과 |
| 현재 RDB baseline | 10m seed scale | High Load, 100 VU, 10분 | dev-default-debug | 단일(1명, round-robin)/VU 기준 | debug-reference | GET /api/user-activities/{userId} | 52.67ms | 109.44ms | 0.01% | 96.29 | 0 | 미계측 | composite API 내부 조회 | 25.80% | active 1, idle 11 | 참고 통과 |
| 현재 RDB baseline | 10m seed scale | High Load, 100 VU, 10분, 5명 round-robin | dev-sql-warn | 5명 round-robin/VU 기준 | multi-user-reference | GET /api/user-activities/{userId} | 38.76ms | 67.89ms | 0.02% | 97.50 | 0 | 미계측 | composite API 내부 조회 | 31.31% | active 1, idle 11 | 다중 사용자 재측정 통과 |
| 현재 RDB baseline | 10m seed scale | Stress, 50 -> 100 -> 200 -> 400 VU | dev-default-debug | 단일(1명, round-robin)/VU 기준 | debug-reference | GET /api/user-activities/{userId} | 1,361.57ms | 1,595.57ms | 0.01% | 103.03 | 0 | 미계측 | composite API 내부 조회 | 2.38% | active 1, idle 11 | 지연 기준 초과 |
| 현재 RDB baseline | 10m seed scale | Stress, 50 -> 100 -> 200 -> 400 VU, 재측정 | dev-sql-warn | 단일(1명, round-robin)/VU 기준 | stress-reference | GET /api/user-activities/{userId} | 101.35ms | 186.74ms | 0.01% | 130.41 | 0 | 미계측 | composite API 내부 조회 | 2.10%(30초 지점) | active 1, idle 10, idle in transaction 1 | 수치 기준 통과, dial i/o timeout 경고 14건 관찰 |
| 현재 RDB baseline | 10m seed scale | Throughput, 150 rps, 총 5회 | dev-default-debug | 단일(1명, round-robin)/pre=max 500 | debug-reference | GET /api/user-activities/{userId} | 평균 35.09ms | 평균 47.56ms | 최대 0.00% | 평균 150.12 | 0 | 미계측 | composite API 내부 조회 | 최대 52.89% | active/idle 위주 | 반복 안정 참고 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 150 rps, 5분, 5명 round-robin | dev-sql-warn | 5명 round-robin/pre=max 500 | multi-user-reference | GET /api/user-activities/{userId} | 21.65ms | 25.56ms | 0.00% | 150.71 | 0 | 미계측 | composite API 내부 조회 | 38.87% | active 1, idle 10, idle in transaction 1 | 다중 사용자 재측정 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 190 rps | dev-default-debug | 단일(1명, round-robin)/pre=max 500 | debug-reference | GET /api/user-activities/{userId} | 70.80ms | 321.32ms | 0.00% | 189.63 | 0 | 미계측 | composite API 내부 조회 | 68.67% | active 2, idle 9, idle in transaction 1 | 단기 경계 참고 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 190 rps, 5분 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | boundary-reference | GET /api/user-activities/{userId} | 32.81ms | 42.47ms | 0.01% | 190.70 | 0 | 미계측 | composite API 내부 조회 | 57.62% | active 1, idle 9, idle in transaction 2 | 경계 통과, dial i/o timeout 경고 7건 관찰 |
| 현재 RDB baseline | 10m seed scale | Throughput, 200 rps, maxVUs 500 | dev-default-debug | 단일(1명, round-robin)/pre=max 500 | debug-reference | GET /api/user-activities/{userId} | 3,198.98ms | 3,361.01ms | 0.00% | 178.96 | 1,034 | 미계측 | composite API 내부 조회 | 56.29% | active 1, idle 8, idle in transaction 3 | 참고 실패, VU 부족 및 DEBUG 로그 영향 가능성 |
| 현재 RDB baseline | 10m seed scale | Throughput, 200 rps, pre/max VU 1000 | dev-sql-warn | 단일(1명, round-robin)/pre=max 1000 | vu-headroom-reference | GET /api/user-activities/{userId} | 46.49ms | 162.73ms | 0.00% | 204.30 | 0 | 미계측 | composite API 내부 조회 | 58.40% | active 1, idle 7, idle in transaction 4 | VU 한도 참고 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 200 rps, pre/max VU 500, 첫 실행 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | warmup-anomaly | GET /api/user-activities/{userId} | 294.49ms | 2,654.48ms | 0.00% | 202.61 | 145 | 미계측 | composite API 내부 조회 | 54.55% | active 3, idle 8, idle in transaction 1 | 첫 실행 `Insufficient VUs` 1건, 최종 기준 제외 |
| 현재 RDB baseline | 10m seed scale | Throughput, 200 rps, pre/max VU 500, 재실행 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | final-baseline | GET /api/user-activities/{userId} | 20.51ms | 24.04ms | 0.00% | 205.15 | 0 | 미계측 | composite API 내부 조회 | 52.82% | active 2, idle 8, idle in transaction 2 | 재실행 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 250 rps, maxVUs 500 | dev-default-debug | 단일(1명, round-robin)/pre=max 500 | debug-reference | GET /api/user-activities/{userId} | 2,737.13ms | 3,027.10ms | 0.00% | 202.58 | 2,266 | 미계측 | composite API 내부 조회 | 86.68% | active 1, idle 11 | 참고 실패, VU 부족 및 DEBUG 로그 영향 가능성 |
| 현재 RDB baseline | 10m seed scale | Throughput, 250 rps, pre/max VU 1000 | dev-sql-warn | 단일(1명, round-robin)/pre=max 1000 | vu-headroom-reference | GET /api/user-activities/{userId} | 163.52ms | 441.06ms | 0.00% | 255.18 | 0 | 미계측 | composite API 내부 조회 | 85.94% | active 1, idle 8, idle in transaction 3 | VU 한도 참고 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 250 rps, pre/max VU 500 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | final-baseline | GET /api/user-activities/{userId} | 24.34ms | 29.55ms | 0.00% | 256.68 | 0 | 미계측 | composite API 내부 조회 | 76.85% | active 2, idle 8, idle in transaction 2 | 응답 기준 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 300 rps, pre/max VU 1000 | dev-sql-warn | 단일(1명, round-robin)/pre=max 1000 | vu-headroom-reference | GET /api/user-activities/{userId} | 613.79ms | 858.04ms | 0.00% | 306.36 | 0 | 미계측 | composite API 내부 조회 | 126.29% | active 1, idle 10, idle in transaction 1 | 참고 실패, VU 부족은 아님 |
| 현재 RDB baseline | 10m seed scale | Throughput, 300 rps, pre/max VU 500 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | final-baseline | GET /api/user-activities/{userId} | 24.63ms | 31.71ms | 0.00% | 307.50 | 0 | 미계측 | composite API 내부 조회 | 91.81% | active 3, idle 9 | 응답 기준 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 200 rps, pre/max VU 500, 3회 반복 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | final-baseline | GET /api/user-activities/{userId} | 평균 24.30ms (20.90~30.12ms) | 평균 30.49ms (24.75~40.26ms) | 최대 0.00% | 평균 205.94 | 0 | 미계측 | composite API 내부 조회 | 최대 63.44% | active/idle 위주 | 3회 반복 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 250 rps, pre/max VU 500, 3회 반복 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | final-baseline | GET /api/user-activities/{userId} | 평균 26.63ms (22.67~29.05ms) | 평균 34.30ms (29.51~38.15ms) | 최대 0.00% | 평균 257.22 | 0 | 미계측 | composite API 내부 조회 | 최대 78.97% | active/idle 위주 | 3회 반복 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 300 rps, pre/max VU 500, 3회 반복 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | final-baseline | GET /api/user-activities/{userId} | 평균 55.63ms (24.35~106.98ms) | 평균 92.92ms (29.66~200.06ms) | 최대 0.00% | 평균 308.77 | 0 | 미계측 | composite API 내부 조회 | 최대 124.29% | active/idle 위주 | 3회 반복 통과, Postgres 컨테이너 CPU(docker stats) 100% 초과 스냅샷 포함 |
| 현재 RDB baseline | 10m seed scale | Throughput, 200 rps, 1분, 5명 round-robin | dev-sql-warn | 5명 round-robin/pre=max 500 | multi-user-reference | GET /api/user-activities/{userId} | 27.76ms | 33.93ms | 0.00% | 204.63 | 0 | 미계측 | composite API 내부 조회 | 47.21% | active 1, idle 10, idle in transaction 1 | 다중 사용자 1분 통과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 250 rps, 1분, 5명 round-robin | dev-sql-warn | 5명 round-robin/pre=max 500 | multi-user-reference | GET /api/user-activities/{userId} | 30.42ms | 40.73ms | 0.00% | 260.75 | 0 | 미계측 | composite API 내부 조회 | 74.15% | active 3, idle 8, idle in transaction 1 | 다중 사용자 1분 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| 현재 RDB baseline | 10m seed scale | Throughput, 300 rps, 1분, 5명 round-robin | dev-sql-warn | 5명 round-robin/pre=max 500 | multi-user-reference | GET /api/user-activities/{userId} | 40.23ms | 87.79ms | 0.00% | 309.95 | 0 | 미계측 | composite API 내부 조회 | 80.99% | active 5, idle 6, idle in transaction 1 | 다중 사용자 1분 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| 현재 RDB baseline | 10m seed scale | Throughput Soak, 100 rps, 30분 | dev-default-debug | 단일(1명, round-robin)/pre=max 500 | soak-reference | GET /api/user-activities/{userId} | 26.71ms | 31.04ms | 0.04% | 100.14 | 0 | 미계측 | composite API 내부 조회 | 28.65% | active 1, idle 11 | 수치 기준 통과, timeout 경고 관찰 |
| 현재 RDB baseline | 10m seed scale | Throughput Soak, 150 rps, 30분 | dev-default-debug | 단일(1명, round-robin)/pre=max 500 | debug-reference | GET /api/user-activities/{userId} | 3,156.80ms | 29,750.32ms | 0.88% | 122.23 | 46,828 | 미계측 | composite API 내부 조회 | 76.08% | active 1, idle 8, idle in transaction 3 | 비교 제외, SQL DEBUG 로그 출력 영향 가능성 |
| 현재 RDB baseline | 10m seed scale | Throughput Soak, 150 rps, 30분 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | soak-reference | GET /api/user-activities/{userId} | 28.40ms | 35.13ms | 0.07% | 150.50 | 0 | 미계측 | composite API 내부 조회 | 38.73% | active 2, idle 10 | 수치 기준 통과, dial i/o timeout 경고 183건 관찰 |
| 현재 RDB baseline | 10m seed scale | Throughput Soak, 190 rps, 30분 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | soak-reference | GET /api/user-activities/{userId} | 37.29ms | 52.11ms | 0.02% | 191.89 | 0 | 미계측 | composite API 내부 조회 | 57.98% | active 2, idle 10 | 수치 기준 통과, dial i/o timeout 경고 53건 관찰 |
| 현재 RDB baseline | 10m seed scale | Throughput Soak, 250 rps, 10분 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | soak-reference | GET /api/user-activities/{userId} | 49.41ms | 136.01ms | 0.02% | 257.65 | 0 | 미계측 | composite API 내부 조회 | 82.73% | active 1, idle 9, idle in transaction 2 | 10분 수치 기준 통과, dial i/o timeout 경고 28건 관찰 |
| 현재 RDB baseline | 10m seed scale | Throughput Soak, 300 rps, 10분 | dev-sql-warn | 단일(1명, round-robin)/pre=max 500 | soak-reference | GET /api/user-activities/{userId} | 1,692.74ms | 1,934.22ms | 0.02% | 307.25 | 1,159 | 미계측 | composite API 내부 조회 | 116.15% | active 1, idle 11 | 실패, dropped/latency 기준 초과 |

인덱스 또는 쿼리 최적화를 반영한 경우에는 SQL/query별 개선 폭을 따로 기록한다.

| SQL/query | 데이터 규모 | baseline median | 최적화 후 median | 개선 폭 | 반영 내용 | 판단 |
| --- | --- | --- | --- | --- | --- | --- |
| 구독 관심사 main |  |  |  |  |  |  |
| 구독 관심사 keywords |  |  |  |  |  |  |
| 최근 작성 댓글 |  |  |  |  |  |  |
| 최근 좋아요한 댓글 |  |  |  |  |  |  |
| 최근 조회 기사 |  |  |  |  |  |  |

전체 측정 완료 후 MongoDB 적용 대상 선정용 비교표를 별도로 작성한다.

| 활동 유형 | 10m optimized SQL/query median | 요청 내 SQL 비용 | join/subquery 비용 | 제외 조건 영향 | API 병목 영향 | MongoDB 후보 판단 |
| --- | --- | --- | --- | --- | --- | --- |
| 구독 중인 관심사 조회 |  |  |  |  |  |  |
| 최근 작성 댓글 조회 |  |  |  |  |  |  |
| 최근 좋아요한 댓글 조회 |  |  |  |  |  |  |
| 최근 조회 기사 |  |  |  |  |  |  |

## 완료 기준

```text
- 100k / 1m / 10m seed scale 데이터 규모별 측정 기준이 정리되어 있다.
- 단일 composite API 기준 k6 시나리오가 정의되어 있다.
- 논리삭제 사용자/기사/댓글 제외 조건이 많은 데이터에서 필터 비용 측정 기준이 정리되어 있다.
- composite API p95/p99와 error rate 판단 기준이 정리되어 있다.
- RPS 기준 측정과 dropped iterations 성공 기준이 정리되어 있다.
- 요청 1건당 SQL 개수와 SQL/query별 join 비용 확인 기준이 정리되어 있다.
- 현재 RDB 기준 baseline 측정 후 인덱스 후보를 반영하고 재측정하는 기준이 정리되어 있다.
- fan-out worst-case, 제외 조건 필터 비용, read/write mixed RDB 측정 결과가 결과 용도별로 분리되어 있다.
- MongoDB 적용 대상 선정을 위한 비교표 형식이 정리되어 있다.
- 실제 성능 측정 결과를 MID4-125와 MID4-179의 MongoDB 후순위 판단에 연결할 수 있다.
```
