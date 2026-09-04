# 후속 적용 검토 흐름 및 결론

[상위 문서](./README.md) | [이전: 카운트 집계 처리 기준](./05-count-aggregation-policy.md) | [다음: RDB 테스트 데이터 생성 기준](./07-rdb-test-data-policy.md)

## 후속 적용 검토 흐름

MongoDB Read Model 적용 필요성을 후속으로 재검토할 때의 전체 흐름은 다음과 같이 진행한다.

```text
1. 활동내역 4개 기능을 RDB로 구현한다.
2. 100k / 1m / 10m seed scale 테스트 데이터를 준비한다.
3. 현재 RDB 스키마와 기존 인덱스 기준으로 baseline을 측정한다.
4. 실행 계획, SQL 개수, full scan, 정렬 및 join 비용을 확인한다.
5. 병목이 확인된 조회에 한해 RDB 인덱스 후보와 쿼리 최적화를 반영한다.
6. 같은 조건에서 재측정하고 p95/p99, DB 부하, SQL 개수, join 비용을 비교한다.
7. 최적화 후에도 기준을 넘는 병목 기능을 MongoDB 후속 적용 후보로 선정한다.
8. 후보 기능에 한해 MongoDB Read Model을 설계한다.
9. 필요한 도메인 이벤트와 outbox 이벤트 저장 기준을 정의한다.
10. outbox worker로 MongoDB Read Model을 비동기 갱신한다.
11. 활동내역 조회 시 MongoDB Read Model 기준으로 DTO를 만들 수 있게 한다.
12. RDB 방식과 MongoDB 방식을 다시 k6로 비교한다.
13. 성능 개선과 운영 복잡도 대비 이득이 확인되면 MongoDB 적용 범위를 확정한다.
```

MongoDB 저장 모델과 이벤트 목록은 4개 활동내역 조회 기능 전체를 후보로 설명하지만, 후속 적용이 확정되면 위 흐름에서 선정된 기능부터 진행한다.

RDB 테스트 데이터는 [RDB 테스트 데이터 생성 기준](./07-rdb-test-data-policy.md)을 따른다.

RDB 조회 성능 측정 시나리오와 결과 기록 표는 [RDB 조회 성능 측정 시나리오](./08-rdb-performance-test-scenarios.md)를 따른다.

MongoDB 후속 적용 시 요청 처리 흐름은 다음과 같이 둔다.

```text
사용자 요청
-> RDB 트랜잭션 시작
-> 원본 데이터 변경
-> 도메인 이벤트 수집
-> payload 직렬화
-> outbox_projection_clock(id=1) row를 PESSIMISTIC_WRITE로 잠금
-> current_version 증가 및 projection_version 발급
-> outbox_events 테이블에 이벤트 저장
-> RDB 커밋
-> 사용자 response 반환
-> outbox worker가 이벤트 처리
-> 필요한 경우 RDB에서 현재 snapshot 또는 집계값 조회
-> activity_histories upsert 또는 숨김 처리
-> *_activity_snapshots 저장 또는 갱신
```

### Outbox worker 현재 구현 경계

MID4-137에서는 위 흐름 중 원본 변경과 `outbox_events` 저장까지 구현했다. 별도 도메인 이벤트 버스에 발행한 뒤 수집하는 구조가 아니라, 각 도메인 서비스가 타입이 지정된 payload record를 만들고 `OutboxEventWriter`를 호출한다. MID4-138에서는 commit된 Outbox를 batch UUID와 lease로 claim해 MongoDB Read Model에 반영하는 다중 인스턴스 worker를 추가했다.

```text
현재 쓰기 요청
-> 기존 RDB 트랜잭션 시작
-> 원본 데이터 변경
-> OutboxEventPayload record 생성
-> 같은 트랜잭션에 MANDATORY로 참여하는 writer 호출
-> payload를 JsonNode로 직렬화
-> outbox_projection_clock(id=1) row를 PESSIMISTIC_WRITE로 잠금
-> current_version 증가 및 projection_version 발급
-> outbox_events 저장
-> RDB 커밋
-> 사용자 response 반환
```

writer는 기존 트랜잭션이 없으면 실행을 거부하며 별도 커밋하지 않는다. payload 직렬화 실패 시 `OutboxPayloadSerializationException`이 발생하고 원본 변경도 함께 롤백된다. 따라서 Outbox row 저장은 논블로킹 작업이 아니라 요청 트랜잭션에 포함된 동기 작업이다.

```text
response 반환 이후
-> worker가 PENDING 또는 재시도 가능한 FAILED를 FOR UPDATE SKIP LOCKED로 batch claim
-> PostgreSQL 시각으로 claim lease를 기록하고 heartbeat로 연장
-> 대상 ID별 RDB 현재 상태, 관계와 count를 batch 조회
-> MongoDB snapshot을 먼저 갱신
-> activity를 natural key 기준으로 순차 atomic upsert 또는 숨김
-> 성공 시 PROCESSED, 실패 시 retry 또는 DEAD_LETTER
```

MongoDB 문서, RDB 현재 상태 batch 재조회, projection writer와 Outbox worker는 구현됐지만 기본 비활성화 상태다. MID4-250은 공통 조회 source와 MongoDB 복합 cursor·snapshot 매핑을 구현하지만 현재 기본 source는 RDB로 유지한다. 설정 기반 MongoDB 전환과 장애 시 RDB fallback은 MID4-139 범위다. MID4-247은 네 가지 count 이벤트를 같은 polling batch의 `event_type + 대상 ID`로 묶어 최고 projection version으로 한 번 반영하고 그룹 전체 상태를 전이한다. MID4-248은 도메인별 물리삭제 cleanup과 낮은 버전의 `PENDING`·`FAILED`·in-flight stale replay가 높은 버전 tombstone을 덮지 못하는 경계를 실제 MongoDB 통합 테스트로 검증한다. MID4-249는 네 활동 유형의 기존 RDB 데이터를 run-id와 checkpoint 기반으로 초기 투영하고 완료 후 정합성 보고서를 저장한다. 상세 실행 계약과 예시는 [초기 데이터 투영 및 정합성 검증](./09-initial-projection-and-reconciliation.md)과 [MongoDB 활동내역 조회 계약](./10-mongodb-query-contract.md)에 기록한다.

동일 ID 복구·재노출은 아직 구현 범위가 아니다. 현재 RDB에는 이를 발생시키는 명령이 없으며, S3 기사 복원은 새 UUID로 기사를 생성한다. 향후 같은 ID 복구 동작이 추가되면 그 transaction에서 event type과 producer를 함께 추가하고, 대상과 부모의 RDB 현재 상태를 다시 확인한 뒤 snapshot과 activity를 복구해야 한다.

여기서 비동기는 사용자 request transaction과 worker 실행 시점이 분리된다는 의미다. worker 내부에서는 RDB 조회와 `MongoTemplate` 명령을 blocking 방식으로 순차 실행하고 각 결과를 확인한 뒤 Outbox 상태를 저장한다.

Outbox 적용에 따른 쓰기 API response 영향은 추정하지 않고 테스트로 확인한다.

기존 쓰기 흐름과 Outbox 적용 후 쓰기 흐름의 차이는 다음과 같다.

```text
기존 쓰기 API
-> 원본 데이터 변경
-> RDB 커밋
-> 사용자 response 반환

Outbox 적용 후 쓰기 API
-> 원본 데이터 변경
-> outbox_events 테이블에 이벤트 저장
-> RDB 커밋
-> 사용자 response 반환
```

따라서 Outbox 적용 후 쓰기 API가 추가로 부담하는 작업은 MongoDB 반영이 아니라 `outbox_events` 저장이다.

MID4-137 이후 쓰기 요청에는 이 저장 비용이 포함되지만 아직 동일 조건 성능 측정을 수행하지 않았다. 기존 MID4-206의 `rdb-mixed-no-outbox` 결과는 측정 당시 상태를 나타내므로 Outbox 적용 후 결과로 재분류하지 않는다. 실제 response time 영향은 수치로 단정하지 않고 후속 동일 조건 성능 테스트로 검증한다.

측정 대상은 다음과 같이 둔다.

```text
- 댓글 작성
- 댓글 수정/삭제
- 댓글 좋아요/좋아요 취소
- 기사 조회 또는 조회수 증가
- 관심사 구독/구독 해제
- 사용자 삭제/탈퇴
```

측정 결과는 다음 기준으로 비교한다.

```text
API | Outbox 적용 전 p95/p99 | Outbox 적용 후 p95/p99 | 증가량 | TPS | error rate | 판단
```

쓰기 API response time은 `요청 시작 -> RDB 커밋 -> response 반환`까지만 측정한다.

outbox worker의 MongoDB 반영 시간은 쓰기 API response time에 포함하지 않고, 별도 지표로 분리한다.

```text
- outbox 처리 지연
- 처리 성공률
- retry 수
- FAILED 이벤트 수
- DEAD_LETTER 이벤트 수
```

기사 조회처럼 발생 빈도가 높은 이벤트는 일반 쓰기 이벤트와 분리해 별도 부하 테스트를 진행한다.

활동내역 API 조회 흐름은 다음과 같이 둔다.

```text
활동내역 API 요청
-> activity_histories에서 userId + type + visible=true 기준 최신순 조회
-> occurredAt DESC, _id DESC 기준으로 cursor/limit 적용
-> targetId 목록 추출
-> 대상 snapshot 컬렉션 조회
-> activity 순서를 유지하며 snapshot 매핑
-> snapshot이 없거나 노출 불가능한 항목 제외
-> DTO 변환
-> 클라이언트 응답
```

커서에는 `occurredAt`과 `_id`를 함께 포함한다. snapshot 누락, `visible=false` 또는 tombstone 항목을 제외한 뒤에는 limit을 채우기 위한 추가 조회를 하지 않는다. 다음 cursor는 마지막 응답 항목이 아닌 마지막 스캔 activity를 기준으로 하므로 짧거나 빈 페이지에서도 scan이 진행된다. 따라서 응답 개수는 요청 limit보다 적을 수 있다.

## 후속 설계 결론

활동내역은 먼저 RDB로 구현한다.

MongoDB는 사전에 특정 기능에 고정해서 적용하지 않고, RDB 최적화 및 성능 검증 이후에도 병목이 남는 조회가 있을 때만 후속 적용 후보로 둔다.

이 문서의 저장 모델과 이벤트 처리 설명은 4개 활동내역 조회 기능 전체의 후보 설계이며, 후속 구현은 선정된 병목 기능부터 시작한다.

MongoDB에는 RDB 전체 데이터가 아니라 DTO 생성을 위한 최소 조회 모델만 저장한다.

RDB는 Source of Truth로 유지하고, MongoDB만 활동내역 조회 최적화를 위해 역정규화한다.

MongoDB Read Model 반영은 비동기로 처리하되, 이벤트 유실을 막기 위해 RDB 원본 변경과 outbox 이벤트 저장은 같은 트랜잭션에서 수행한다.

Outbox payload는 JSON 계열 타입으로 저장한다. 운영 DB가 PostgreSQL이면 `JSONB`, MySQL이면 `JSON`을 우선하고, 테스트 DB나 호환성 제약이 있으면 `TEXT` fallback을 허용한다. event ID, event type, aggregate type/ID, actor user ID, occurredAt은 Outbox 공통 envelope 컬럼에 두고, payload에는 이를 중복하지 않은 이벤트별 body만 저장한다.

Outbox 적용으로 쓰기 API response가 얼마나 증가하는지는 추정하지 않고, Outbox 적용 전/후 성능 테스트 결과를 기준으로 판단한다.

MongoDB 반영은 response 반환 이후 worker가 비동기로 수행하므로, 쓰기 API response 측정 범위와 분리한다.

카운트 집계값은 MongoDB 반영만을 위해 RDB counter를 바로 만들지 않고, 기본적으로 worker가 RDB에서 현재 집계값을 다시 조회해 MongoDB snapshot에 반영한다.

MID4-247은 같은 polling batch의 count 이벤트를 `event_type + snapshot 대상 ID`로 병합한다. 그룹별 최고 `projection_version` 이벤트만 RDB source batch 조회와 MongoDB projection에 전달하고, 선택된 그룹 row 전체를 같은 결과로 전이한다.

```text
동일 batch에 ARTICLE_VIEW_COUNT_CHANGED(A1) 두 건 선택
-> A1의 현재 viewCount를 batch query로 조회
-> 두 row 중 가장 높은 projection_version으로 MongoDB projection 1회
-> 두 row 전체를 동일 processed_at의 PROCESSED로 bulk update
-> 실패 시 같은 원인을 기록하되 row별 retry_count로 FAILED 또는 DEAD_LETTER 결정
```

사용자가 같은 대상에 대해 같은 종류의 활동을 반복하면 activity를 계속 추가하지 않고 `userId + type + targetType + targetId` 기준으로 기존 activity를 upsert한다.

MID4-135에서 이 natural key의 unique index를 준비했고 MID4-138 worker가 atomic upsert를 구현했다. 같은 outbox 이벤트가 재처리되거나 동일 활동 이벤트가 중복 발행되어도 activity가 중복 생성되지 않도록 보장한다.

natural key와 atomic upsert는 중복 문서 방지 계약이다. activity의 `visible`, `status`, `occurredAt` 같은 mutable 상태는 payload의 과거 상태를 그대로 반영하지 않고 worker가 좋아요·구독 관계, actor 사용자의 존재·논리삭제 상태, 원본과 부모 대상의 존재 및 노출 여부를 RDB에서 다시 조회해 계산한다. 삭제된 actor의 지연 이벤트는 새 activity를 만들지 않고 기존 `USER_DELETED` 상태를 유지한다.

같은 polling batch의 RDB 조회는 commentId, articleId, interestId, userId 같은 대상 ID 집합으로 묶는다. 중복 이벤트나 실패 후 재시도, transaction commit 순서와 worker 처리 순서의 역전이 발생해도 각 처리는 당시의 RDB 현재 상태를 반영하며, 나중에 commit된 transaction의 이벤트가 최종 상태를 다시 반영한다. `occurredAt`은 현재 관계 row의 시각 또는 검증된 불변 이벤트 시각을 사용하고 `$max` 또는 동등한 단조 조건을 적용한다.

각 worker는 claim한 batch 내부의 MongoDB 쓰기를 순차 실행하고 완료를 기다리며, 여러 인스턴스는 서로 겹치지 않는 이벤트 row batch를 병렬 처리한다. 상태 갱신은 claim UUID 소유권 조건으로 보호하고 lease 만료 경계의 MongoDB 중복 쓰기는 natural key와 atomic upsert로 중복 생성을 막는다.

서로 다른 batch가 같은 target의 이벤트를 포함할 수 있으므로 claim 자체는 target별 projection을 직렬화하지 않는다. producer가 singleton clock row 잠금을 원본 transaction commit까지 유지해 `projection_version`을 commit 순서와 맞추고, MongoDB는 결정적 `_id`의 저장 version이 incoming보다 작은 경우만 갱신한다. 따라서 두 worker가 RDB 결과를 역순으로 저장해도 `visible`, `status`, snapshot 표시값은 더 낮은 버전으로 회귀하지 않는다. `occurredAt`은 live write에서 `$max`도 함께 유지한다.

```text
W1: 댓글 C1의 이전 상태 조회
W2: 댓글 C1의 최신 상태 조회 및 MongoDB 반영
W1: 이전 상태를 나중에 MongoDB 반영
-> W2의 projectionVersion=42가 먼저 저장됨
-> W1의 projectionVersion=41은 저장 version < incoming version 조건을 만족하지 못함
-> stale 성공(no-op)으로 처리되어 content, visible, status는 회귀하지 않음
```

원본 엔티티별 `@Version`, advisory lock과 target별 worker 직렬화는 추가하지 않는다. Outbox projection fencing token은 `outbox_projection_clock` singleton row의 비관적 잠금으로 발급한다. worker의 짧은 claim transaction에는 별도로 `FOR UPDATE SKIP LOCKED`를 사용하고 MongoDB 반영 중에는 RDB row lock을 유지하지 않는다. Outbox row와 version 저장은 원본 변경과 같은 request transaction에서 동기 수행하지만, MongoDB 반영과 RDB 현재 상태 batch 재조회는 response 반환 이후 worker가 별도 thread에서 blocking 방식으로 수행한다.

삭제와 닉네임 변경 payload는 원본 변경 전에 activity natural key와 comment snapshot ID를 수집한다. 이 목록은 수집 transaction이 관찰한 snapshot이므로 worker는 목록만 유일한 cleanup 근거로 삼지 않는다. 삭제 버전보다 오래된 기존 문서는 사용자/대상/부모 조건의 versioned bulk cleanup으로 잡고, payload key는 문서가 없어도 hidden guard 또는 tombstone으로 물질화하며, 아직 처리되지 않은 과거 이벤트는 actor/source row 재확인과 CAS로 활성화를 막는다.

댓글 내용, 기사 제목/요약/게시일, 관심사 키워드, count 집계값처럼 나중 이벤트로 바뀔 수 있는 snapshot 필드는 오래된 payload로 덮어쓰지 않고, worker 처리 시점의 RDB 현재값을 조회해 반영한다.

원본 엔티티별 `source_version`은 사용하지 않는다. 서로 다른 aggregate의 fan-out을 하나의 순서로 비교할 수 있는 전역 `projection_version`과 MongoDB CAS를 기본안으로 사용한다.

댓글 작성 또는 댓글 좋아요처럼 기사에 종속된 activity는 `parentTargetType=ARTICLE`, `parentTargetId=articleId`를 함께 저장한다. 기사 삭제 또는 비공개 처리 시 이 부모 식별자로 해당 기사에 속한 댓글 activity를 숨김 처리한다.

좋아요 취소, 구독 해제와 사용자/기사/댓글 논리삭제처럼 기존 활동내역에서 더 이상 노출되면 안 되는 현재 이벤트는 기존 activity를 삭제하지 않고 `visible=false`로 변경한다. 현재 관심사는 별도 비노출 이벤트가 없고 제거 시 물리삭제 tombstone을 남긴다. 관심사 비노출은 후속 이벤트 후보에만 해당한다.

논리삭제 이벤트는 기존에 `visible=true`인 activity만 숨김 처리한다. 이미 취소, 구독 해제, 다른 삭제 사유로 숨겨진 activity의 `status`는 덮어쓰지 않는다.

`status`는 activity가 노출되는지와 별개로 현재 상태 또는 숨김 사유를 표현한다. 기본 상태는 `ACTIVE`이며, 좋아요 취소는 `CANCELED`, 구독 해제는 `UNSUBSCRIBED`, 기사/댓글 논리삭제와 기사 비공개 처리는 `TARGET_DELETED`, 사용자 삭제 또는 탈퇴는 `USER_DELETED`로 둔다. 후속 관심사 비노출 이벤트를 추가한다면 `TARGET_DELETED`를 적용한다.

`TARGET_DELETED`로 숨긴 activity에는 `hiddenByTargetType`, `hiddenByTargetId`를 함께 저장해 어떤 대상의 삭제 또는 비노출 전파로 visible=true activity가 숨겨졌는지 기록한다. 이미 숨겨진 activity는 다른 삭제 사유로 이 값이 갱신되지 않을 수 있으므로, 대상 복구 이벤트는 `hiddenByTargetType`, `hiddenByTargetId` 일치만으로 복구 후보를 제한하지 않는다.

대상 복구 시에는 activity만 `ACTIVE`로 되돌리지 않는다. 복구 대상과 관련될 수 있는 `status=TARGET_DELETED` activity 후보를 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 찾고, 각 activity의 대상과 필요한 부모 대상이 RDB 기준으로 현재 노출 가능한 상태인지 다시 계산한다. 남은 차단 원인이 없으면 대상 snapshot을 RDB 현재 값으로 갱신해 `visible=true`로 복구한 뒤 activity를 `visible=true`, `status=ACTIVE`로 복구한다. `CANCELED`, `UNSUBSCRIBED`, `USER_DELETED` 상태는 대상 복구 이벤트로 자동 복구하지 않는다.

사용자, 기사, 댓글, 관심사가 RDB에서 최종 물리삭제되면 MongoDB Read Model의 관련 문서는 실제 remove하지 않고 scrubbed tombstone으로 바꾼다. `_id`, `projectionVersion`, `tombstone=true`, `visible=false`, `updatedAt`만 남기고 사용자·대상 ID와 표시 필드를 제거한다. 물리삭제 이후에는 복구를 고려하지 않고, 복구 가능성은 논리삭제 상태에서만 유지한다.

물리삭제 이후 삭제 전 지연 이벤트가 도착하면 worker는 RDB source row 부재를 확인하고 같은 논리 `_id`의 더 높은 tombstone version 때문에 stale no-op으로 끝낸다. 동일 버전 재시도도 이미 반영된 문서는 건너뛰고 빠진 fan-out 문서만 이어서 반영한 뒤 outbox row를 `PROCESSED`로 전환할 수 있다.

최종 구조는 다음과 같다.

```text
RDB 원본 데이터
-> 활동 이벤트 발생
-> 같은 RDB transaction에서 global projection version 발급 후 outbox_events 저장
-> outbox worker가 대상 ID별 RDB 현재 상태를 batch 조회
-> activity_histories와 snapshot을 결정적 _id + projectionVersion CAS로 멱등 갱신
-> 활동내역 API 조회
-> activity_histories 조회
-> 대상 snapshot 조회 및 매핑
-> DTO 변환
-> 클라이언트 응답
```

이 방식은 MongoDB를 단순 학습용으로 끼워 넣는 것이 아니라, RDB 조회 병목을 측정하고 근거를 기반으로 MongoDB Read Model을 적용하는 방향이다.
