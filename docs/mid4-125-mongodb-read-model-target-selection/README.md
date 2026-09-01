# MID4-125 MongoDB 조회용 데이터 적용 대상 선정

> 이전 작업: [MID4-179 RDB 처리량 한계 측정](../mid4-179-rdb-throughput-limit/README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md) · 다음 작업: [MID4-206 확장 성능 측정](../mid4-206-mongodb-k6-compare.md)

## 이 문서가 답하는 질문

RDB 인덱스를 적용한 뒤에도 MongoDB에 조회용 데이터를 따로 만들어야 할 만큼 느린 활동내역 조회가 남았는지 판단한다.

## 한눈에 보는 결론

- 현재 측정 조건에서는 네 가지 활동내역 모두 RDB를 유지한다.
- 인덱스 적용 후 가장 큰 테스트 데이터에서도 각 SQL의 실행 시간 가운데 값은 `1.283 ms` 이하였다.
- API도 초당 20건을 요청 누락 없이 처리했으므로 MongoDB를 바로 도입할 근거가 부족했다.
- 다만 한 관심사나 댓글에 연결 데이터가 많이 몰리는 상황은 충분히 확인하지 않았으므로 후속 측정 대상으로 남겼다.
- 목표 처리량과 응답 시간 기준이 정해지고 RDB가 그 기준을 넘을 때 MongoDB 적용을 다시 검토한다.

결론은 MongoDB 적용 취소가 아니라 `후순위`다. 지금은 RDB를 기준 데이터 저장소로 유지하고, 실제 병목이 확인될 때 조회용 데이터 복사본을 추가한다.

## 읽기 전에 알아둘 말

| 용어 | 쉬운 의미 |
| --- | --- |
| 조회용 데이터(Read Model) | 화면 조회에 필요한 모양으로 미리 저장한 데이터 |
| 기준 데이터(Source of Truth) | 가장 신뢰할 수 있는 원본 데이터이며 이 문서에서는 RDB를 뜻함 |
| 조회용 복사본(projection) | 원본 변경을 반영해 MongoDB 등에 저장하는 사용자별 활동 데이터 |
| 공용 정보 사본(snapshot) | 기사·댓글·관심사처럼 여러 사용자가 함께 보는 정보를 따로 저장한 값 |
| 목표 기준(SLO) | 서비스가 만족해야 하는 처리량과 응답 시간 기준 |
| 변경 이벤트 대기열(Outbox) | RDB 변경 내용을 다른 저장소에 안전하게 전달하기 위해 먼저 기록하는 테이블 |
| 이벤트 내용(payload) | 변경 이벤트 한 건에 담는 실제 데이터 |

## 어떤 결과를 바탕으로 판단했나

| Jira | 문서 | 판단에 사용한 내용 |
| --- | --- | --- |
| MID4-132 | [RDB baseline](../mid4-132-activity-history-rdb-baseline/README.md) | RDB 최적화 전 10m scale API 실패와 SQL 병목 후보 |
| MID4-133 | RDB 인덱스 migration | 활동내역 read-path 인덱스 반영 |
| MID4-134 | [RDB optimized remeasure](../mid4-134-rdb-optimized-remeasure/README.md) | RDB 최적화 후 API/SQL 재측정 결과 |
| MID4-179 | [RDB throughput limit](../mid4-179-rdb-throughput-limit/README.md) | RDB 최적화 후 최대 요청량과 실패 구간 측정 |
| MID4-96 | [MongoDB/Redis 적용 판단 기록](../mid4-96-mongodb-decision-record/README.md) | MongoDB 사전 설계와 후순위 결정 근거 |

## 비교 조건

- API: `GET /api/user-activities/{userId}`
- 대상 사용자: `00000001-0000-4000-8000-000000000001`
- k6 조건: 20 rps, 1m, `preAllocatedVUs=20`, `maxVUs=100`
- SQL 측정: 3회 warm-up 후 `EXPLAIN (ANALYZE, BUFFERS)`, 5회 반복 실행 median
- seed scale: `100k`, `1m`, `10m`은 테이블별 row 수가 아니라 `seed_activity_history(scale_count)` 입력값
- API p95/p99/RPS는 `GET /api/user-activities/{userId}` composite API 기준이다. 활동 유형별 MongoDB 후보 판단에는 SQL/query별 측정값을 사용한다.

## API 전체 결과 비교

아래 API 비교는 한 요청에서 네 가지 활동내역을 조합하는 `GET /api/user-activities/{userId}` 기준이다. 현재 API 요청만으로는 구독 관심사, 최근 댓글, 좋아요 댓글, 조회 기사별 RPS와 p95/p99를 분리 산출하지 않는다.

| 테스트 데이터 | 개선 전 p95 | 인덱스 적용 후 p95 | 개선 전 p99 | 인덱스 적용 후 p99 | 개선 전 RPS | 인덱스 적용 후 RPS | 개선 전 요청 누락 | 인덱스 적용 후 요청 누락 | 오류율 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100k | 53.58 ms | 24.29 ms | 63.40 ms | 28.53 ms | 20.01 | 21.01 | 0 | 0 | 0.00% |
| 1m | 474.02 ms | 21.82 ms | 657.60 ms | 25.69 ms | 19.97 | 21.01 | 0 | 0 | 0.00% |
| 10m | 32353.24 ms | 19.25 ms | 43167.63 ms | 22.63 ms | 3.11 | 21.02 | 979 | 0 | 0.00% |

MID4-132 baseline에서는 10m scale에서 20 rps를 따라가지 못했지만, MID4-133 인덱스 반영 후 MID4-134에서는 100k, 1m, 10m 모두 dropped iteration 없이 통과했다. 현재 조건만으로는 MongoDB Read Model을 적용해야 할 API 병목이 남았다고 보기 어렵다.

## 조회별 SQL 비용 비교

요청 1건은 현재 구현 기준으로 6개 SQL로 구성된다. 활동 유형별 후보 판단은 이 SQL/query 측정값과 join/subquery 비용을 기준으로 한다.

| 순서 | SQL | 주요 비용 |
| ---: | --- | --- |
| 1 | 사용자 조회 | users PK 조회, `deleted_at` 확인 |
| 2 | 최근 조회 기사 | article_views, articles, users join, 댓글 수 subquery, 조회 수 subquery |
| 3 | 최근 작성 댓글 | comments, users, articles join, 좋아요 수 subquery |
| 4 | 최근 좋아요한 댓글 | comment_likes, comments, users, articles join, 좋아요 수 subquery |
| 5 | 구독 관심사 main | subscriptions, interests, users join, 관심사별 구독자 수 subquery |
| 6 | 구독 관심사 keywords | `keywords.interest_id = any (?)` batch 조회 |

| 조회 | 10m 개선 전 가운데 값 | 10m 인덱스 적용 후 가운데 값 | 변화 | MongoDB 후보 판단 |
| --- | ---: | ---: | ---: | --- |
| 최근 작성 댓글 | 82.747 ms | 0.476 ms | -99.42% | 제외 |
| 최근 좋아요한 댓글 | 45.905 ms | 0.684 ms | -98.51% | 제외 |
| 최근 조회 기사 | 1825.932 ms | 0.525 ms | -99.97% | 제외 |
| 구독 중인 관심사 total | 11.635 ms | 1.283 ms | -88.97% | 후속 관찰 |

최근 작성 댓글, 최근 좋아요한 댓글, 최근 조회 기사는 `LIMIT 10` 범위에서 최종 반환 row가 작고, 최적화 후 10m scale median이 모두 1ms 미만이다. count subquery를 분리하거나 MongoDB snapshot으로 옮길 근거는 현재 수치상 부족하다.

구독 중인 관심사는 10m scale에서도 total median이 1.283ms로 낮다. 다만 이번 seed에서는 target user 구독 수가 50건, keywords 결과가 150건으로 고정되어 있어 사용자별 구독 수 또는 관심사별 구독자 수 fan-out이 크게 늘어나는 worst-case까지 검증한 결과는 아니다. 따라서 MongoDB 적용 대상이 아니라 후속 관찰 대상으로 둔다.

## DB 사용량으로 본 판단

MID4-134 optimized 10m baseline 중간 Docker stats는 PostgreSQL CPU `9.63%`, memory `2.329GiB / 30.91GiB`였다. baseline 직후 `pg_stat_database` 기준 cache hit은 `100.00%`, temp file은 `0`, deadlock은 `0`이었다.

RDB baseline의 10m 실패 원인은 최근 조회 기사 read-path 인덱스 부재였고, MID4-133 인덱스 반영 후 같은 API 조건에서 DB 부하와 dropped iteration이 안정화됐다. 현재는 DB 부하를 이유로 MongoDB Read Model을 도입할 근거가 부족하다.

## 최종 선택

| 조회 | 선정 결과 | 사유 |
| --- | --- | --- |
| 최근 작성 댓글 | RDB 유지 | 10m optimized median 0.476ms, API dropped 0 |
| 최근 좋아요한 댓글 | RDB 유지 | 10m optimized median 0.684ms, API dropped 0 |
| 최근 조회 기사 | RDB 유지 | baseline 병목이었지만 인덱스 후 10m median 0.525ms |
| 구독 중인 관심사 | RDB 유지, fan-out 후속 관찰 | 현재 total median 1.283ms, 단 worst-case fan-out은 미측정 |

MongoDB Read Model 적용 대상은 현재 선정하지 않는다. 결론은 `후순위`이며, MongoDB dev 환경구성도 이번 작업 범위에서는 진행하지 않는다.

## 나중에 MongoDB를 적용한다면

현재는 적용하지 않지만, 후속 측정에서 MongoDB 적용이 필요해질 경우 범위는 다음 기준으로 제한한다.
아래 항목은 확정 schema가 아니라 Read Model 적용이 필요해졌을 때의 검토 기준이다.

- RDB는 Source of Truth로 유지한다.
- MongoDB는 활동내역 조회 전용 Read Model로만 사용한다.
- `activity_histories`는 사용자별 활동내역 조회 응답을 빠르게 구성하기 위한 projection 후보로 둔다.
- 사용자별 활동 projection이 필요하면 `userId`, `activityType`, `sourceEntityId` 조합을 식별 키 후보로 검토한다.
- 대상 표시 정보를 별도 snapshot으로 둘 경우 공용 대상 snapshot과 사용자별 활동 projection을 분리하는 방향을 우선 검토한다.
- 공용 대상 snapshot에는 화면 응답에 필요한 최소 대상 필드만 저장하고, 사용자별 활동 시각, 정렬 기준, 노출 상태, 취소 상태는 넣지 않는 방향을 우선 검토한다.
- 공용 대상 snapshot 식별 키는 `sourceEntityType`, `sourceEntityId` 조합을 후보로 둔다.
- 최근 작성 댓글, 최근 좋아요한 댓글, 최근 조회 기사처럼 최대 10건만 필요한 영역은 MongoDB 적용 전 RDB 인덱스와 SQL 구조를 먼저 재검증한다.
- 구독 관심사는 사용자별 구독 수 또는 관심사별 구독자 수 fan-out이 SLO를 넘는 경우에만 snapshot 후보로 올린다.

## 삭제 데이터 처리 기준

MongoDB Read Model을 후속 적용하는 경우 삭제 상태 반영 기준은 MID4-96 사전 설계 계약을 따른다.
현재 티켓에서는 구현하지 않지만, cleanup 이후 지연 이벤트나 재처리 이벤트가 삭제된 projection 또는 snapshot을 재생성하지 않는 기준까지 설계 계약으로 남긴다.

- 사용자 논리삭제: `/api/user-activities/{userId}`는 기존 RDB 동작과 동일하게 `UserNotFoundException` 기준 `404`를 반환한다. MongoDB projection에 `USER_DELETED` 상태를 저장하더라도 API 응답에는 노출하지 않는다.
- 기사/댓글 논리삭제: 관련 활동 item은 기존 RDB 조회처럼 API 결과에서 제외하거나, 필요한 경우 `TARGET_DELETED` 상태를 내부 projection 상태로만 유지한다.
- 물리삭제: RDB source row 삭제 시 연결된 MongoDB projection과 snapshot도 cleanup 대상에 포함한다.
- cleanup은 RDB 삭제 정책과 같은 이벤트 또는 배치 기준으로 맞추며, MongoDB 문서만 독립적으로 정리하지 않는다.
- 지연 이벤트나 재처리 이벤트는 upsert 전에 RDB source row 존재 여부를 확인하고, source row가 없으면 MongoDB 문서를 재생성하지 않고 no-op 및 `PROCESSED` 처리한다.
- MID4-96 설계에서는 activity 상태 전이 순서 보호 기준을 직렬화된 outbox `event_sequence`와 activity `lastAppliedEventSequence` 비교로 구체화했다.
- 후속 검토 항목은 `eventId` 중복 처리 방식, aggregate별 `version` 도입 여부, 삭제 이벤트 우선순위, tombstone 또는 삭제 처리 기록 보존 기간, 운영자 재처리 절차다.

## 변경 이벤트에 담을 정보 기준

- PostgreSQL: `JSONB`
- MySQL: `JSON`
- 테스트 DB: `TEXT fallback`
- payload 내부 필드를 DB에서 직접 조회하는 요구가 없으면 JSON path/index는 만들지 않는다.
- payload는 이벤트 재처리와 projection 갱신에 필요한 최소 정보만 담는 방향으로 검토한다.
- MongoDB Read Model 적용 시 outbox 이벤트 후보에는 `eventId`, `eventSequence`, `eventType`, `aggregateType`, `aggregateId`, `occurredAt`, 삭제 여부, 영향 받는 사용자 식별 정보를 포함할 수 있다.
- `payload.eventSequence`는 직렬화 조건을 만족한 `outbox_events.event_sequence`를 payload에 담은 값이며, 별도 도메인 sequence나 aggregate version이 아니다. worker는 이 값을 MongoDB `activity_histories.lastAppliedEventSequence`에 저장하고, activity 상태 전이는 `eventSequence > lastAppliedEventSequence` 조건으로 보호한다.
- 일반 projection 갱신 이벤트의 `activityType`, 활동 record ID, `sourceEntityType`, `sourceEntityId`, 활동 시각, 화면 응답용 대상 데이터 전달 방식은 후속 구현 티켓에서 확정한다.
- 화면 응답용 대상 데이터를 payload에 직렬화할지, worker가 RDB에서 재조회할지는 이번 티켓에서 결정하지 않는다.
- 기사/댓글 삭제처럼 여러 사용자 활동에 영향을 줄 수 있는 이벤트는 reverse index 조회 또는 사용자별 이벤트 fan-out 중 어떤 방식을 사용할지 후속 설계에서 결정한다.

## Redis를 사용하지 않은 이유

Redis는 영구 저장소가 아니므로 활동내역 Read Model 저장소로 사용하지 않는다. 현재 측정 결과에서는 캐시가 필요한 병목도 확인되지 않았으므로 `미적용`으로 둔다. 후속으로 Redis를 검토한다면 짧은 TTL의 응답 캐시나 반복 조회 보조 용도에 한정한다.

## 최종 의사결정과의 관계

MID4-96의 MongoDB/Redis 적용 여부 판단에는 이 문서, [MID4-179 RDB 최대 요청량 측정](../mid4-179-rdb-throughput-limit/README.md), [MongoDB/Redis 적용 판단 기록](../mid4-96-mongodb-decision-record/README.md)을 근거로 연결한다. 현재 MID4-125 결론은 MongoDB `후순위`, Redis `미적용`이며, Outbox payload 타입은 DB별 JSON 계열 타입과 테스트 DB fallback 기준만 남긴다. MongoDB 환경구성은 MID4-96 또는 별도 후속 티켓에서 `적용` 결론이 확정된 뒤 진행한다.

## 완료 조건 대응

| 완료 조건 | 대응 |
| --- | --- |
| 단일 활동내역 API와 요청 내부 SQL/query의 RDB 성능 측정 결과 비교 | composite API 비교, SQL 및 Join 비용 비교 |
| p95/p99, SQL 개수, DB 부하, join 비용 기준 병목 선정 | composite API 비교, SQL 및 Join 비용 비교, DB 부하 판단 |
| MongoDB 적용 여부 결론 | `후순위` |
| activity_histories와 snapshot 저장 범위 | MongoDB 적용 시 범위 기준 |
| 사용자/기사/댓글 논리삭제와 물리삭제 cleanup | 삭제 및 Cleanup 기준 |
| Outbox payload 타입 | Outbox Payload 기준 |
| JSON path/index 생성 기준 | payload 내부 필드 조회 요구가 없으면 미생성 |
| Redis 적용 여부와 사용 목적 | Redis 적용 여부 |

## 다시 검토할 조건

MongoDB 환경구성 또는 Read Model 구현은 다음 조건 중 하나가 충족될 때 별도 티켓으로 진행한다.

- 목표 RPS, p95/p99 SLO, 허용 error rate, dropped iteration 기준이 확정된다.
- 확정된 기준으로 재측정했을 때 RDB 최적화 후에도 composite API가 목표 기준을 넘고 특정 SQL/query 병목이 확인된다.
- 구독 관심사 fan-out worst-case 측정에서 MongoDB snapshot 내부 `subscriberCount` 또는 keywords 조립 비용이 병목으로 확인된다. 현재 API DTO가 `interestSubscriberCount`를 사용하는 경우에는 조회 모델의 `subscriberCount`를 응답 DTO에서 `interestSubscriberCount`로 매핑한다.
