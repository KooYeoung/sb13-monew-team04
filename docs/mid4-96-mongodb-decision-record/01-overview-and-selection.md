# 개요 및 적용 대상 선정

[상위 문서](./README.md) | [다음: MongoDB 저장 모델](./02-mongodb-storage-model.md)

## MongoDB를 사용하는 목적

MongoDB는 RDB를 대체하기 위한 저장소가 아니라, 활동내역 조회 시 RDB의 조회 및 join 부담을 줄이기 위한 조회 최적화용 저장소로 사용한다.

기본 역할은 다음과 같이 구분한다.

```text
RDB = 원본 데이터 기준(Source of Truth)
MongoDB = 활동내역 조회 전용 Read Model
DTO = API 응답을 위한 표현 모델
```

즉, MongoDB에 RDB 전체 데이터를 복사하지 않는다. MongoDB에는 활동내역 화면 또는 API 응답을 만들기 위해 필요한 최소 조회 데이터를 저장한다.

## 1차 구현 방향

활동내역 기능은 먼저 RDB 기준으로 구현한다.

1차 구현 대상은 다음과 같다.

```text
1. 구독 중인 관심사 조회
2. 최근 작성 댓글 조회
3. 최근 좋아요한 댓글 조회
4. 최근 조회 기사
```

이 단계에서는 MongoDB를 바로 적용하지 않는다. 먼저 RDB 기준으로 기능의 정확성과 baseline 성능을 확인하고, 실행 계획상 필요성이 확인된 경우에만 인덱스와 쿼리 최적화를 반영한다.

MongoDB 적용 대상도 사전에 특정 기능으로 고정하지 않는다. RDB 구현 및 성능 검증 이후에도 병목이 남는 조회 기능을 후속 적용 후보로 선정한다.

이 문서 묶음의 MongoDB 저장 모델과 이벤트 처리 설계는 아래 4개 활동내역 조회 기능 전체를 후보로 설명한다. 다만 후속 적용이 확정되면 성능 검증 후 선정된 병목 기능부터 적용하고, 나머지 기능은 같은 기준을 확장 적용할 수 있는 후보로 둔다.

## MongoDB 적용 대상 선정 기준

MongoDB를 적용하게 될 경우, 적용 대상은 아래 4개 활동내역 조회 기능 중 RDB 최적화 이후에도 성능이 가장 떨어지는 기능으로 선정한다.

```text
1. 구독 중인 관심사 조회
2. 최근 작성 댓글 조회
3. 최근 좋아요한 댓글 조회
4. 최근 조회 기사
```

선정 기준은 단순 평균 응답 시간이 아니라 다음 항목을 함께 본다.

```text
- composite API p95 응답 시간
- composite API p99 응답 시간
- 요청 1건당 SQL 개수
- SQL/query별 RDB 실행 시간
- join 대상 테이블 수
- DB CPU 사용률
- 커넥션 풀 대기 시간
- 데이터 증가에 따른 성능 저하 폭
- 에러율
- dropped iterations
```

현재 제공된 API는 `GET /api/user-activities/{userId}` 하나이며, 이 API는 한 요청에서 네 가지 활동내역을 조합한다. 따라서 별도 endpoint 또는 query-level runtime 계측이 없으면 활동 유형별 API RPS와 p95/p99를 산출하지 않는다. API p95/p99와 RPS는 composite API 기준으로 보고, 활동 유형별 MongoDB 후보 선정에는 SQL/query별 측정값을 사용한다.

아래는 판단 방식 설명을 위한 SQL/query 기준 가상 예시이며, 이번 측정의 실제 결론은 아니다.

예를 들어 성능 측정 결과가 다음과 같다면:

```text
구독 중인 관심사 SQL/query median: 80ms
최근 작성 댓글 SQL/query median: 130ms
최근 좋아요한 댓글 SQL/query median: 210ms
최근 조회 기사 SQL/query median: 160ms
```

이 가상 예시에서는 `최근 좋아요한 댓글 조회`를 MongoDB Read Model 후속 적용 후보로 우선 검토한다.

다만 현재 MID4-125와 MID4-179 측정 결과에서는 위와 같은 병목 조회가 확정되지 않았으므로, 이번 단계의 실제 결론은 MongoDB Read Model `후순위`다.

## 성능 검증 방식

성능 검증은 k6를 사용한다.

상세 시나리오와 결과 기록 표는 [RDB 조회 성능 측정 시나리오](./08-rdb-performance-test-scenarios.md)를 따른다.

테스트 데이터는 실제 테이블별 row 수가 아니라 `seed_activity_history(scale_count)` 입력값 기준의 seed scale로 준비한다.

```text
100k seed scale
1m seed scale
10m seed scale
```

각 seed scale의 실제 테이블별 row count는 seed 함수의 도메인 분포에 따라 달라지므로, MID4-132, MID4-134, MID4-179의 측정 시점별 row count 표를 기준으로 해석한다.

MongoDB Read Model을 후속 구현하게 될 경우에는 RDB 방식과 MongoDB Read Model 방식을 동일한 seed scale과 동일한 보강 k6 시나리오에서 비교한다. 현재 MID4-125와 MID4-179에서는 MongoDB 구현 전후 비교를 수행하지 않았다.

### k6 시나리오

```text
Smoke Test
- 1~5 VU
- 1분
- 스크립트, 인증, 응답 검증 목적

Baseline Test
- 10~20 VU
- 3~5분
- 기본 성능 비교 목적

Average Load Test
- 50 VU
- 10분
- 일반적인 동시 사용 상황 가정

High Load Test
- 100 VU
- 10분
- 병목 발생 여부 확인

Stress Test
- 50 -> 100 -> 200 -> 400 VU
- 각 단계 3~5분
- 어느 지점부터 성능이 무너지는지 확인
```

VU 기준 측정과 별도로 RPS 기준 측정을 필수 보강 시나리오로 수행한다.

RPS 기준은 composite API 요청 스케줄을 따라가는지 확인하기 위한 시나리오이며, 목표 RPS와 `dropped_iterations=0`을 함께 성공 조건으로 본다.

```text
50 req/s
100 req/s
200 req/s
```

## 병목 판단 기준

단일 SQL 실행 시간이 짧다고 해서 병목이 아니라고 단정하지 않는다.

예를 들어 SQL 하나가 0.3ms여도 다음과 같은 상황에서는 DB에 큰 부하가 발생할 수 있다.

```text
요청 1건당 SQL 10개
초당 요청 1,000건
=> 초당 SQL 10,000개
```

따라서 병목 여부는 서로 다른 단위의 값을 더해 하나의 점수로 만들지 않고, 다음 지표를 함께 평가해 판단한다.

```text
- 단일 쿼리 실행 시간
- 초당 요청 수
- 요청당 SQL 개수
- DB 리소스 사용률
- 커넥션 풀 대기 시간
```

임시 성공 기준은 다음과 같이 둔다.

아래 값은 사전 후보 판단 기준이며, MID4-179의 최종 처리량 판정 기준(`p95 < 1000ms`, `p99 < 2000ms`)과는 별도로 본다.

```text
http_req_failed < 1%
dropped_iterations = 0
p95 < 200ms
p99 < 500ms
DB CPU 지속 70% 미만
커넥션 풀 대기 거의 없음
```

위 기준을 만족하지 못하거나, RDB 최적화 이후에도 composite API가 목표 p95/p99를 넘고 특정 SQL/query 비용이 병목으로 확인되면 해당 활동 유형을 MongoDB Read Model 적용 후보로 검토한다.
