# RDB 조회 성능 측정 시나리오

[상위 문서](./README.md) | [이전: RDB 테스트 데이터 생성 기준](./07-rdb-test-data-policy.md)

## 목적

이 문서는 단일 활동내역 API와 요청 내부 SQL/query를 RDB 기준으로 측정하기 위한 성능 테스트 시나리오를 정리한다.

MongoDB Read Model 적용 여부는 이 문서에서 결정하지 않는다. 이 문서의 결과 표와 병목 판단 기준을 바탕으로 `MID4-125`에서 MongoDB 적용 대상을 선정한다.

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
- 같은 endpoint가 반복 호출될 때 DB CPU 또는 커넥션 대기 병목을 만드는 경우
```

인덱스를 추가한 경우에는 적용 전후를 같은 데이터 규모와 같은 k6 시나리오에서 비교한다.

## k6 시나리오

후속 보강 시나리오에서는 단일 활동내역 API인 `GET /api/user-activities/{userId}`를 같은 부하 단계로 측정한다.

| 시나리오 | 부하 | 시간 | 목적 |
| --- | --- | --- | --- |
| Smoke | 1~5 VU | 1분 | 인증, endpoint, 응답 검증 확인 |
| Baseline | 10~20 VU | 3~5분 | 기본 응답 시간과 SQL 비용 확인 |
| Average Load | 50 VU | 10분 | 일반 부하에서 p95/p99 확인 |
| High Load | 100 VU | 10분 | 병목 발생 여부 확인 |
| Stress | 50 -> 100 -> 200 -> 400 VU | 각 단계 3~5분 | 성능이 무너지는 지점 확인 |

VU 기준 측정과 별도로 RPS 기준 측정을 필수 보강 시나리오로 수행한다.

RPS 기준 측정은 요청 스케줄을 따라가는지 확인하기 위한 시나리오이며, 목표 RPS와 `dropped_iterations=0`을 함께 성공 조건으로 본다.

```text
50 req/s
100 req/s
200 req/s
```

RPS 기준 측정은 `GET /api/user-activities/{userId}` composite API에만 적용한다. 별도 endpoint 또는 query-level 계측이 없으면 구독 관심사, 최근 댓글, 좋아요 댓글, 조회 기사별 RPS와 p95/p99를 결과로 기록하지 않는다.

## 요청 구성

대표 사용자 ID 순환은 후속 보강 k6 시나리오에서 검토한다. 현재 측정 근거는 `00000001-0000-4000-8000-000000000001` 단일 target user 기준이다.

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
| RDB | 쿼리 실행 시간, 실행 계획, 인덱스 사용 여부, DB CPU, slow query |
| 비교 판단 | composite API p95/p99 증가율, SQL/query별 join 비용, error rate |

임시 성공 기준은 다음과 같이 둔다.

아래 값은 초기 후보 판단 기준이며, MID4-179의 최종 처리량 판정 기준(`p95 < 1000ms`, `p99 < 2000ms`)과는 별도로 본다.

```text
http_req_failed < 1%
dropped_iterations = 0
p95 < 200ms
p99 < 500ms
DB CPU 지속 70% 미만
커넥션 풀 대기 거의 없음
```

프로젝트 환경에서 위 기준을 그대로 만족하기 어렵다면 composite API의 데이터 증가에 따른 악화 폭과 SQL/query별 상대 비용을 함께 본다.

## 현재 k6 측정 한계

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

따라서 현재 문서에서는 k6 부족분을 한계로 명시하고, multi-user, fan-out worst-case, 장시간 soak, 반복 측정, read/write 혼합 부하 검증은 별도 Jira 티켓에서 보강한다.

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

| 측정 단계 | 데이터 규모 | 시나리오 | API | p95 | p99 | error rate | RPS | dropped iterations | 요청당 SQL | 주요 join | DB CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 현재 RDB baseline | 100k seed scale | Smoke | GET /api/user-activities/{userId} |  |  |  |  |  |  |  |  |  |  |

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
- MongoDB 적용 대상 선정을 위한 비교표 형식이 정리되어 있다.
- 실제 성능 측정 결과를 MID4-125와 MID4-179의 MongoDB 후순위 판단에 연결할 수 있다.
```
