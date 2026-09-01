# 활동내역 조회 성능 개선 기록 안내서

## 이 문서가 답하는 질문

활동내역 조회 성능과 관련된 여러 측정 문서를 어떤 순서로 읽어야 하는지 안내한다. 각 작업에서 무엇을 확인했고 다음 작업으로 왜 이어졌는지 먼저 설명한 뒤, 필요한 상세 측정값과 실행계획으로 이동할 수 있게 구성했다.

## 한눈에 보는 현재 결론

- 처음에는 데이터가 늘어날수록 사용자 활동을 찾는 SQL이 전체 테이블을 넓게 읽어 API가 크게 느려졌다.
- 사용자 조건과 최신순 조회에 맞는 인덱스를 추가하자 일반 데이터 조건에서는 SQL과 API가 크게 빨라졌다.
- 로컬 단기 측정에서는 RDB가 `200 rps`까지 통과했지만, 더 긴 측정에서는 `190 rps`를 보수적인 통과 구간으로 확인했다.
- 일반 조회만 보면 MongoDB 조회용 데이터를 바로 도입할 근거가 부족해 적용을 후순위로 두었다.
- 하지만 한 댓글·기사·관심사에 연결 데이터가 몰리거나 제외할 데이터가 많으면 반복 조회 비용이 다시 커졌다.
- 활동 데이터에 노출 상태를 저장하고 직접 확인하도록 바꾸자 이 특수 조건도 크게 개선됐다.
- 남은 문제는 결과마다 댓글 수·조회 수·구독자 수를 다시 세는 과정과 비활성 데이터를 읽은 뒤 제외하는 일부 인덱스 경로다.

## 권장 읽기 순서

| 순서 | 작업 | 이 문서에서 확인할 질문 | 다음 작업으로 이어진 이유 |
| ---: | --- | --- | --- |
| 1 | [MID4-132 개선 전 기준선](mid4-132-activity-history-rdb-baseline/README.md) | 데이터가 늘어날 때 어디가 가장 느려졌는가 | 사용자별 활동을 찾는 인덱스가 부족하다는 사실을 확인했다. |
| 변경 | MID4-133 인덱스 적용 | 무엇을 바꿨는가 | 사용자 조건과 최신순 정렬에 맞는 인덱스를 추가했다. |
| 2 | [MID4-134 인덱스 적용 후 재측정](mid4-134-rdb-optimized-remeasure/README.md) | 인덱스가 실제로 효과가 있었는가 | 일반 데이터 조건에서 큰 병목이 사라져 처리 가능한 요청량을 확인할 수 있게 됐다. |
| 3 | [MID4-179 처리 가능한 요청량 측정](mid4-179-rdb-throughput-limit/README.md) | 짧은 측정에서 RDB가 어디까지 버티는가 | 단일 사용자·1분 조건의 통과와 실패 경계를 확인했다. |
| 4 | [MID4-125 MongoDB 적용 대상 선정](mid4-125-mongodb-read-model-target-selection/README.md) | 지금 MongoDB를 적용해야 하는가 | 일반 조건에서는 RDB가 충분해 MongoDB 적용을 후순위로 결정했다. |
| 5 | [MID4-206 확장 성능 측정](mid4-206-mongodb-k6-compare.md) | 장시간·데이터 몰림·제외·읽기/쓰기 혼합 조건도 괜찮은가 | 데이터가 특정 대상에 몰리거나 제외 데이터가 많을 때 남은 병목을 확인했다. |
| 변경 | MID4-219와 후속 조회 변경 | 무엇을 바꿨는가 | 연결된 사용자의 삭제 여부를 반복 조회하지 않고 활동 데이터의 `visibility_status`를 확인하도록 바꿨다. |
| 6 | [MID4-227 노출 상태 적용 전후 재측정](mid4-227-rdb-bottleneck-remeasure/README.md) | 같은 특수 조건에서 변경 전후 차이는 무엇인가 | 반복 사용자 조회는 사라졌고, 집계 하위 조회와 일부 인덱스 경로가 다음 개선 후보로 남았다. |

## 궁금한 내용으로 바로 가기

| 궁금한 내용 | 먼저 볼 문서 |
| --- | --- |
| 개선 전 가장 느린 SQL | [MID4-132 개선 전 기준선](mid4-132-activity-history-rdb-baseline/README.md) |
| 인덱스 적용 전후의 API·SQL 차이 | [MID4-134 인덱스 적용 후 재측정](mid4-134-rdb-optimized-remeasure/README.md) |
| 짧은 요청량 단계에서 통과와 실패 경계 | [MID4-179 처리 가능한 요청량 측정](mid4-179-rdb-throughput-limit/README.md) |
| MongoDB 적용을 미룬 이유와 재검토 조건 | [MID4-125 MongoDB 적용 대상 선정](mid4-125-mongodb-read-model-target-selection/README.md) |
| 일반·장시간·데이터 몰림·제외·혼합 부하 결과 | [MID4-206 확장 성능 측정](mid4-206-mongodb-k6-compare.md) |
| `visibility_status` 적용 효과와 남은 병목 | [MID4-227 노출 상태 적용 전후 재측정](mid4-227-rdb-bottleneck-remeasure/README.md) |

## 작업별 핵심 결과가 달라 보이는 이유

문서마다 데이터와 실행 시간이 다르므로 숫자만 떼어 직접 비교하면 안 된다. 각 결과는 아래 질문에 맞춰 사용한다.

| 작업 | 측정의 중심 | 결과를 사용할 때 주의할 점 |
| --- | --- | --- |
| MID4-132 | 개선 전 데이터 크기별 API와 SQL | 인덱스 적용 전 결과다. |
| MID4-134 | MID4-132와 같은 방식의 인덱스 적용 후 결과 | 인덱스 자체의 개선 효과를 비교할 때 사용한다. |
| MID4-179 | 단일 사용자, 10m 테스트 데이터, 요청량별 1분 측정 | p95 `1,000 ms`, p99 `2,000 ms` 기준의 단기 결과이며 운영 보장값이 아니다. |
| MID4-206 | 여러 사용자, 반복·장시간·특수 데이터·혼합 부하 | p95 `200 ms`, p99 `500 ms`의 더 엄격한 기준을 사용한다. 보수적인 장시간 통과 구간은 `190 rps`다. |
| MID4-227 | 데이터 몰림과 제외 조건에서 변경 전후를 같은 순서로 반복 측정 | 일반 조회의 최대 처리량이 아니라 `visibility_status` 변경 효과를 보는 결과다. |

따라서 MID4-179의 `200 rps`와 MID4-206의 `190 rps`는 서로 모순되는 값이 아니다. 앞의 값은 1분 단기 경계이고, 뒤의 값은 더 엄격한 응답 시간 기준으로 30분 동안 확인한 보수적인 구간이다.

## 세부 자료 찾아가기

### 개선 전 기준선

- [API 전체 결과](mid4-132-activity-history-rdb-baseline/api-baseline.md)
- [최근 작성 댓글 SQL](mid4-132-activity-history-rdb-baseline/recent-comments.md)
- [최근 좋아요한 댓글 SQL](mid4-132-activity-history-rdb-baseline/recent-liked-comments.md)
- [최근 조회 기사 SQL](mid4-132-activity-history-rdb-baseline/recent-article-views.md)
- [구독 관심사 SQL](mid4-132-activity-history-rdb-baseline/subscribed-interests.md)

### 인덱스 적용 후

- [API 전체 결과](mid4-134-rdb-optimized-remeasure/api-remeasure.md)
- [최근 작성 댓글 SQL](mid4-134-rdb-optimized-remeasure/recent-comments.md)
- [최근 좋아요한 댓글 SQL](mid4-134-rdb-optimized-remeasure/recent-liked-comments.md)
- [최근 조회 기사 SQL](mid4-134-rdb-optimized-remeasure/recent-article-views.md)
- [구독 관심사 SQL](mid4-134-rdb-optimized-remeasure/subscribed-interests.md)

### 노출 상태 적용 전후

- [최근 작성 댓글 실행계획](mid4-227-rdb-bottleneck-remeasure/recent-comments.md)
- [최근 좋아요한 댓글 실행계획](mid4-227-rdb-bottleneck-remeasure/recent-liked-comments.md)
- [최근 조회 기사 실행계획](mid4-227-rdb-bottleneck-remeasure/recent-article-views.md)
- [구독 관심사 실행계획](mid4-227-rdb-bottleneck-remeasure/subscribed-interests.md)

## 공통 용어 설명

| 용어 | 쉬운 의미 |
| --- | --- |
| 개선 전 기준선(baseline) | 개선 효과를 비교하기 위해 먼저 측정한 값 |
| 인덱스 적용 후(optimized) | 사용자 조건과 최신순 조회에 맞는 인덱스를 추가한 상태 |
| 테스트 데이터 크기(seed scale) | 테스트 데이터를 만드는 함수에 전달한 크기이며 테이블별 실제 데이터 수와는 다름 |
| RPS | 1초에 보내는 요청 수 |
| 가상 사용자(VU) | 동시에 API를 호출하도록 부하 도구가 만든 사용자 |
| p95 / p99 | 대부분의 요청뿐 아니라 느린 요청까지 포함해 보는 응답 시간 |
| 요청 누락(dropped iterations) | 서버가 느려 정해진 시간에 시작하지 못한 요청 수 |
| 데이터 몰림(fan-out) | 한 댓글·기사·관심사에 좋아요·조회·구독이 많이 연결된 상태 |
| 제외 데이터 조건(exclusion) | 삭제되거나 비활성인 데이터가 많이 섞여 조회 중 걸러내야 하는 상태 |
| 실행 시간 가운데 값(median) | 반복 측정값을 순서대로 놓았을 때 가운데 값 |
| 하위 조회(subquery) | 결과 한 건에 필요한 개수 등을 계산하기 위해 SQL 안에서 다시 실행하는 조회 |
| 실행계획(EXPLAIN) | DB가 데이터와 인덱스를 어떤 순서로 읽었는지 보여주는 기록 |
| 조회용 데이터(Read Model) | 화면 조회에 필요한 모양으로 MongoDB 등에 미리 저장한 데이터 |
| 노출 상태(visibility status) | 활동을 화면과 개수 계산에 포함할 수 있는지 활동 데이터에 저장한 값 |

## 다음에 확인할 내용

- 비활성 데이터를 읽은 뒤 제외하는 조회에 활성 데이터만 담는 부분 인덱스를 적용했을 때 효과를 측정한다.
- 기사별 댓글·조회 수와 관심사별 구독자 수를 결과마다 다시 계산하는 구조가 데이터 증가 시 어느 지점에서 한계에 도달하는지 확인한다.
- 목표 처리량과 응답 시간 기준이 확정되면 같은 조건에서 RDB와 MongoDB 조회용 데이터를 비교한다.
- 측정 조건이 달라지면 기존 숫자를 그대로 운영 기준으로 사용하지 않고 같은 환경에서 다시 측정한다.
