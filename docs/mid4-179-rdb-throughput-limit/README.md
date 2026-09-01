# MID4-179 RDB 최적화 후 처리 가능한 요청량 측정

> 이전 작업: [MID4-134 인덱스 적용 후 재측정](../mid4-134-rdb-optimized-remeasure/README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md) · 다음 작업: [MID4-125 MongoDB 적용 대상 선정](../mid4-125-mongodb-read-model-target-selection/README.md)

## 이 문서가 답하는 질문

인덱스를 적용한 RDB가 로컬 테스트 조건에서 활동내역 조회 요청을 초당 몇 건까지 안정적으로 처리하는지 확인한다.

## 한눈에 보는 결론

- 1분 측정에서는 초당 `200건`까지 요청 누락과 응답 시간 기준 초과 없이 통과했다.
- 초당 `250건`부터는 DB CPU가 90% 이상으로 올라가고 요청 누락과 응답 시간 기준 초과가 함께 발생했다.
- 따라서 이 측정 조건에서 확인한 안정 구간의 상한은 `200 rps`다.
- 이 수치는 로컬 개발 환경과 단일 사용자 기준이므로 운영 환경의 보장값으로 사용하지 않는다.
- 이후 MID4-206에서는 측정 시간과 사용자 분포를 넓혀 장시간 기준의 보수적인 통과 구간을 다시 확인했다.

## 읽기 전에 알아둘 말

| 용어 | 쉬운 의미 |
| --- | --- |
| RPS | 1초에 보내는 요청 수 |
| 가상 사용자(VU) | 동시에 API를 호출하도록 부하 도구가 만든 사용자 |
| 요청 누락(dropped iterations) | 서버가 느려 부하 도구가 정해진 시간에 시작하지 못한 요청 수 |
| p95 / p99 | 느린 요청까지 포함해 보는 응답 시간 기준 |
| 측정된 RPS | 부하 도구가 전체 실행 구간을 기준으로 계산한 실제 요청 처리율 |

## 어떤 조건에서 측정했나

- API: `GET /api/user-activities/{userId}`
- 대상 사용자: `00000001-0000-4000-8000-000000000001`
- 기준 데이터: MID4-134와 동일한 10m seed scale
- DB size: `3655 MB`
- k6 executor: `constant-arrival-rate`
- 측정 시간: 각 단계 `1m`
- VU 설정: `preAllocatedVUs=500`, `maxVUs=500`
- measured RPS: k6 `handleSummary`가 저장한 `http_reqs.rate` 기준
- 성공 판단: `error rate < 1%`, `checks rate > 99%`, `dropped iterations = 0`, `p95 < 1000 ms`, `p99 < 2000 ms`
- p95/p99/checks threshold는 activity-history k6 script의 기본 threshold와 동일하게 둔다.

10m seed의 실제 측정 시점 row count는 다음과 같다.

| 테이블 | 데이터 수 |
| --- | ---: |
| users | 100,000 |
| interests | 50,000 |
| keywords | 150,000 |
| subscriptions | 500,045 |
| articles | 1,999,802 |
| comments | 4,000,296 |
| comment_likes | 3,000,000 |
| article_views | 2,999,999 |

## 응답 시간만으로 처리량을 계산하지 않은 이유

응답 시간이 30ms라고 해서 단일 처리 흐름에서 300 rps가 바로 가능한 것은 아니다.

```text
단일 직렬 처리량 = 1000ms / 30ms ~= 33 rps
동시 처리량 10개가 30ms를 유지하면 = 10 / 0.03s ~= 333 rps
```

따라서 최대 요청량은 평균 응답 시간만으로 확정하지 않고, k6의 실제 도착률, VU 사용량, dropped iterations, p95/p99, DB 부하를 함께 확인한다.

결과 표의 `measured RPS`는 k6 summary의 `http_reqs.rate` 값을 그대로 기록한 것이다. k6가 집계한 전체 실행 구간 기준 Counter rate이므로 `requests / 60초`로 계산한 값과 다를 수 있다. 요청량 판단은 `target rate`, `checks rate`, `dropped iterations`, p95/p99 threshold를 함께 본다.

## 요청량별 결과

| 목표 요청량 | 처리 요청 수 | 측정된 RPS | 요청 누락 | 오류율 | 응답 검증 통과율 | 평균 | p95 | p99 | 판단 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 50 rps | 3,001 | 53.31 | 0 | 0.00% | 100.00% | 19.86 ms | 24.98 ms | 29.29 ms | pass |
| 100 rps | 6,001 | 106.60 | 0 | 0.00% | 100.00% | 19.49 ms | 25.11 ms | 29.38 ms | pass |
| 150 rps | 9,001 | 160.13 | 0 | 0.00% | 100.00% | 20.54 ms | 25.76 ms | 31.19 ms | pass |
| 200 rps | 12,001 | 213.38 | 0 | 0.00% | 100.00% | 21.97 ms | 32.20 ms | 88.07 ms | pass |
| 250 rps | 13,563 | 233.66 | 1,438 | 0.00% | 100.00% | 1813.86 ms | 2515.98 ms | 2683.07 ms | dropped/threshold fail |
| 300 rps | 14,158 | 243.99 | 3,842 | 0.00% | 100.00% | 1918.65 ms | 2406.79 ms | 2557.73 ms | dropped/threshold fail |

## DB 사용량

30초 지점 `docker stats --no-stream` 단일 시점 샘플 기준이다. 실행 전체 평균이나 최대값이 아니다.

| 목표 요청량 | PostgreSQL CPU | PostgreSQL 메모리 | 참고 |
| ---: | ---: | ---: | --- |
| 50 rps | 12.69% | 2.445 GiB | stable |
| 100 rps | 28.42% | 2.445 GiB | stable |
| 150 rps | 51.63% | 2.446 GiB | stable |
| 200 rps | 70.45% | 2.446 GiB | stable |
| 250 rps | 93.05% | 2.446 GiB | dropped/duration fail |
| 300 rps | 92.60% | 2.446 GiB | dropped/duration fail |

## 이 결과로 내린 판단

이번 로컬 dev 측정에서 확인된 안정 상한은 `200 rps`이다. `250 rps`부터는 HTTP error는 없지만 PostgreSQL CPU가 90% 이상으로 올라가고 dropped iterations와 p95/p99 threshold 초과가 같이 발생했다.

처음 `200 rps`를 `preAllocatedVUs=100`으로 실행했을 때는 dropped iterations가 발생했지만, `preAllocatedVUs=500`으로 재측정하자 dropped 없이 통과했다. 따라서 최종 결과는 load generator의 VU 준비 영향을 줄인 `preAllocatedVUs=500` 값을 기준으로 본다.

MID4-125 MongoDB Read Model 판단에는 다음처럼 연결한다.

1. 활동내역 API 요구 처리량이 `200 rps` 이하라면, 현재 RDB 인덱스 최적화 상태만으로도 Read Model을 바로 적용할 근거는 약하다.
2. 요구 처리량이 `250 rps` 이상이거나 p95/p99 목표가 현재보다 엄격하면, 활동내역 조회를 MongoDB Read Model 또는 별도 캐시/조회 모델 후보로 올릴 수 있다.
3. `250 rps` 이상을 목표로 잡을 경우에는 Read Model 구현 전후 동일 seed와 k6 조건으로 재측정해야 한다.

## 실행 방법

```powershell
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 50
```

여러 단계를 실행할 때는 rate만 바꿔 반복한다.

```powershell
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 50
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 100
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 150
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 200
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 250 -AllowFailure
.\docs\mid4-179-rdb-throughput-limit\raw\run-throughput-limit.ps1 -Rate 300 -AllowFailure
```

## 원본 자료

- [seed-10m.out](raw/seed-10m.out)
- [snapshot-10m.txt](raw/snapshot-10m.txt)
- [activity-history-smoke-summary.json](raw/activity-history-smoke-summary.json)
- [activity-history-50rps-summary.json](raw/activity-history-50rps-summary.json)
- [activity-history-100rps-summary.json](raw/activity-history-100rps-summary.json)
- [activity-history-150rps-summary.json](raw/activity-history-150rps-summary.json)
- [activity-history-200rps-summary.json](raw/activity-history-200rps-summary.json)
- [activity-history-250rps-summary.json](raw/activity-history-250rps-summary.json)
- [activity-history-300rps-summary.json](raw/activity-history-300rps-summary.json)
