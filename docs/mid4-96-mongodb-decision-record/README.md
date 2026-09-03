# MID4-96 MongoDB/Redis 적용 여부 판단 기록

> 활동내역 성능 측정의 전체 흐름은 [활동내역 조회 성능 개선 기록 안내서](../activity-history-performance-guide.md)에서 먼저 확인할 수 있다.

## 결론

이번 배포 전에는 활동내역 조회 경로에 MongoDB Read Model과 Redis를 적용하지 않는다.

MID4-125와 MID4-179 측정 결과 기준으로 RDB 인덱스 최적화 후 `GET /api/user-activities/{userId}`는 10m seed에서 `200 rps`까지 안정적으로 처리됐다. 당시에는 MongoDB 환경 구성부터 Outbox, projection 동기화, 삭제 전파, 장애 재처리 정책까지 배포 전에 추가할 만큼의 성능 병목이 확인되지 않았다.

이후 MID4-135에서 후속 구현과 비교 검증을 위한 MongoDB 연결 설정, 로컬 Compose, 컬렉션 이름과 인덱스 초기화 기반을 준비했다. MID4-136에서는 RDB `outbox_events` 기본 테이블과 JPA 저장 모델을 추가했고, MID4-246에서 payload와 RDB 재조회 책임을 구분하는 수렴 정책을 확정했다. MID4-137에서는 사용자·관심사·기사·댓글 도메인의 변경 트랜잭션이 Outbox 이벤트를 함께 저장하도록 producer를 연동했다. MID4-138에서는 Outbox worker와 RDB 현재 상태 batch 재조회, MongoDB projection writer를 추가했다. MID4-247에서는 같은 polling batch의 count 이벤트를 그룹화해 중복 projection과 상태 전이를 병합했다. MID4-248에서는 도메인별 물리삭제 cleanup과 stale replay 차단을 통합 검증했다. worker는 기본 비활성화 상태이며 운영 조회 경로는 계속 RDB를 사용한다.

따라서 현재 판단은 MongoDB Read Model을 배포 범위에 포함하지 않고, RDB를 활동내역 조회의 기준 구현이자 Source of Truth로 유지하는 것이다. Redis도 활동내역 Read Model 저장소나 캐시로 적용하지 않는다.

이 디렉터리의 `01`~`08` 문서는 MongoDB 적용 가능성을 검토하기 위해 작성한 사전 설계 기록이다. 최종 적용 여부는 이 README, MID4-125, MID4-179 측정 결과를 기준으로 판단한다.

따라서 이번 단계의 의사결정은 다음과 같다.

| 항목 | 결정 |
| --- | --- |
| MongoDB Read Model | 후순위 |
| Redis | 미적용 |
| RDB | Source of Truth로 유지 |
| 배포 조회 경로 | RDB 최적화 상태 유지 |

## MID4-248까지의 현재 구현 상태

MID4-135 이후 MID4-248까지의 작업은 MongoDB 적용 결론을 변경하지 않고, 후속 구현과 비교 검증에 필요한 환경, Outbox 저장 구조, producer와 비동기 projection을 단계적으로 추가했다.

| 구분 | 현재 상태 |
| --- | --- |
| 연결과 실행 환경 | Spring Data MongoDB 의존성, dev/prod/test 설정, 로컬 `mongo:8.0` Compose 준비 |
| 활성화 정책 | dev/prod 기본 비활성화, test 비활성화 |
| 컬렉션과 인덱스 | `activity_histories`와 세 snapshot 컬렉션 이름 및 인덱스 초기화 준비 |
| 로컬 권한 | root와 애플리케이션 계정 분리, 애플리케이션 계정은 대상 DB `readWrite`만 사용 |
| Outbox 기본 저장 | PostgreSQL JSONB payload, 처리 상태와 retry 필드를 가진 `outbox_events`, JPA 엔티티와 repository 준비 |
| Outbox producer | 20개 `OutboxEventType`, 도메인별 payload record와 writer를 추가하고 사용자·관심사·기사·댓글 변경 트랜잭션에 이벤트 저장 연동 |
| 저장 계약 | 공통 envelope는 개별 컬럼, payload body는 JSONB로 저장하며 writer는 기존 트랜잭션에 필수 참여 |
| MongoDB 문서 | activity와 댓글·기사·관심사 snapshot 문서, 결정적 SHA-256 `_id`, `projectionVersion`, hidden guard와 scrubbed tombstone 정의 |
| Outbox worker | `PENDING`과 재시도 시각이 지난 `FAILED`를 `SKIP LOCKED`와 lease로 batch claim하고 RDB 현재 상태를 조회해 version CAS upsert, 숨김 또는 tombstone 처리 |
| count batch 병합 | 4개 count 이벤트를 같은 polling batch의 `event_type + snapshot 대상 ID`로 묶고 최고 projection version으로 MongoDB를 한 번 갱신한 뒤 그룹 전체 상태 전이. 현재 네 이벤트에서 snapshot 대상 ID는 `aggregate_id`와 같다. |
| 삭제·stale replay 검증 | 댓글·기사·관심사·사용자 물리삭제의 bulk cleanup과 payload fan-out tombstone을 실제 MongoDB projection에 반영하고, 더 낮은 version의 `PENDING`·`FAILED`·in-flight stale 쓰기가 tombstone을 되살리지 못함을 검증 |
| 실패 처리 | 1분, 5분, 15분, 1시간 retry 후 5회 실패 시 `DEAD_LETTER`; 단건 또는 count 그룹 상태 저장은 독립 트랜잭션으로 처리 |
| 다중 인스턴스 순서 보호 | commit 순서와 일치하는 전역 projection version 및 MongoDB CAS로 서로 다른 batch의 동일 target stale write 차단 |
| 아직 구현하지 않은 범위 | 복구·재노출, 초기 투영, MongoDB 조회 경로 전환, 운영 재처리 |

따라서 현재 API는 계속 RDB를 조회하지만, MID4-137에서 연동한 쓰기 요청은 원본 변경과 같은 RDB 트랜잭션에 Outbox row를 생성한다. payload 직렬화 뒤 전역 clock row를 잠그는 버전 발급도 요청 처리 중 동기 수행되므로 서로 무관한 쓰기 요청 사이에 잠깐의 대기가 생길 수 있다. MID4-138에서 구현하고 MID4-247에서 count 그룹 처리를 확장한 worker의 RDB 재조회와 MongoDB 반영은 response 반환 이후 별도 thread에서 blocking 방식으로 수행된다. 여러 인스턴스에서 worker를 활성화할 수 있으며, 각 실행은 batch UUID와 lease를 원자적으로 기록해 서로 다른 이벤트 row를 병렬 처리한다. worker polling 인덱스는 성능 측정 없이 추가하지 않는다.

MongoDB와 RDB 상태 변경은 하나의 트랜잭션이 아니므로 전달 보장은 at-least-once다. producer는 singleton clock row를 요청 트랜잭션 종료까지 잠가 commit 순서의 `projection_version`을 발급한다. MongoDB writer는 natural key의 결정적 `_id`와 저장 version이 incoming보다 작은 경우만 반영해 서로 다른 worker의 동일 target stale overwrite를 막는다. 취소/해제는 hidden guard를, 물리삭제는 식별·표시 필드를 지운 tombstone을 문서가 없어도 남긴다. 상세 계약은 [MongoDB 저장 모델](./02-mongodb-storage-model.md#activity-생성-및-수정-기준)과 [Outbox worker 동시성 설명](./04-outbox-design.md#다중-worker-실행과-projection-version-cas)에 기록한다.

현재 RDB 도메인에는 논리삭제된 대상의 같은 ID 복구·재노출 동작이 없다. S3 기사 복원은 새 UUID의 기사를 생성하므로 기존 Read Model 복구가 아니다. 따라서 MID4-248은 사용되지 않는 복구 event type이나 handler를 미리 만들지 않고 현재 존재하는 삭제와 stale replay만 검증했다.

MID4-248의 MongoDB projection 통합 테스트는 실제 MongoDB writer와 handler를 사용하고 claim, source 조회, Outbox 상태 저장은 test double로 격리한다. stale 성공 후 worker가 `markProcessed`를 요청하는 데까지 확인하며, claim의 동시성·소유권과 상태의 RDB 영속화는 기존 PostgreSQL 및 상태 서비스 테스트가 별도로 담당한다.

## k6 측정 해석 범위

MID4-179의 k6 결과는 이번 의사결정의 참고 근거로 사용하되, 운영 환경의 보장값으로 보지 않는다.

현재 결과는 로컬 dev 환경, 단일 target user, 10m seed, 각 요청량 단계 1분 측정 기준이다. multi-user 분포, 구독 관심사 fan-out worst-case, 장시간 soak, 반복 측정에 따른 편차, read/write 혼합 부하, MongoDB 구현 전후 비교는 아직 검증하지 않았다.

따라서 `200 rps`는 현재 조건에서 확인한 RDB 최적화 상태의 참고 상한이며, k6 보강 측정은 별도 Jira 티켓에서 준비한다.

## 재검토 조건

다음 조건 중 하나가 확인되면 MongoDB Read Model 적용을 다시 검토한다.

- 활동내역 API 목표 처리량이 `250 rps` 이상으로 확정된다.
- 현재보다 엄격한 p95/p99 SLO가 정해지고 RDB 최적화 상태에서 기준을 넘는다.
- 구독 관심사 fan-out worst-case에서 MongoDB snapshot 내부 `subscriberCount` 또는 keywords 조립 비용이 병목으로 확인된다.
- RDB 인덱스와 SQL 구조를 재검증한 뒤에도 특정 활동내역 조회가 병목으로 남는다.

현재 RDB 활동내역 DTO는 구독 관심사 응답 필드로 `interestSubscriberCount`를 사용한다. MongoDB Read Model을 후속 적용할 경우 snapshot 내부 필드는 `subscriberCount`로 유지하고, API 응답 DTO 변환 시 `subscriberCount -> interestSubscriberCount`로 매핑한다.

## 근거 문서

| 문서 | 내용 |
| --- | --- |
| [01-overview-and-selection.md](./01-overview-and-selection.md) | MongoDB 사용 목적과 적용 대상 선정 기준 |
| [02-mongodb-storage-model.md](./02-mongodb-storage-model.md) | 적용 시 `activity_histories`와 snapshot 저장 모델 후보 |
| [03-event-handler-targets.md](./03-event-handler-targets.md) | 적용 시 필요한 이벤트 핸들러 후보 |
| [04-outbox-design.md](./04-outbox-design.md) | RDB 원본 변경과 MongoDB 반영을 분리하기 위한 Outbox 설계 |
| [05-count-aggregation-policy.md](./05-count-aggregation-policy.md) | count 집계값 반영 기준 |
| [06-final-flow-and-conclusion.md](./06-final-flow-and-conclusion.md) | 후속 적용 검토 흐름과 판단 기준 |
| [07-rdb-test-data-policy.md](./07-rdb-test-data-policy.md) | RDB 기준 테스트 데이터 생성 기준 |
| [08-rdb-performance-test-scenarios.md](./08-rdb-performance-test-scenarios.md) | RDB 조회 성능 측정 시나리오 |

## 관련 문서

- [활동내역 조회 성능 개선 기록 안내서](../activity-history-performance-guide.md)
- [MID4-125 MongoDB Read Model 적용 대상 선정](../mid4-125-mongodb-read-model-target-selection/README.md)
- [MID4-132 RDB baseline 성능 측정](../mid4-132-activity-history-rdb-baseline/README.md)
- [MID4-134 RDB 최적화 후 성능 재측정](../mid4-134-rdb-optimized-remeasure/README.md)
- [MID4-179 RDB 최적화 후 최대 요청량 측정](../mid4-179-rdb-throughput-limit/README.md)
- [MongoDB Read Model 로컬 환경 설정](../environment-setup.md#7-mongodb-read-model-로컬-설정)
