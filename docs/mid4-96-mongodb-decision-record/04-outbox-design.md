# Outbox 설계

[상위 문서](./README.md) | [이전: 이벤트 핸들러 대상](./03-event-handler-targets.md) | [다음: 카운트 집계 처리 기준](./05-count-aggregation-policy.md)

## 동시성 및 Outbox 처리

MongoDB Read Model을 비동기로 반영하려면 RDB 원본 변경과 MongoDB 반영 사이의 정합성 문제가 생긴다.

가장 피해야 할 방식은 RDB 저장과 MongoDB 저장을 요청 흐름에서 각각 직접 수행하는 것이다.

```text
RDB 저장 성공
MongoDB 저장 실패
-> RDB에는 원본 데이터가 있지만 MongoDB 활동내역에는 누락될 수 있다.

RDB 저장 실패
MongoDB 저장 성공
-> 원본에는 없는 데이터가 MongoDB 활동내역에 남을 수 있다.
```

따라서 MongoDB Read Model을 후속 적용하게 된다면 Transactional Outbox Pattern을 기본 설계 후보로 둔다.

```text
RDB 트랜잭션 안
-> 원본 데이터 변경
-> outbox_events 테이블에 이벤트 저장

RDB 트랜잭션 밖
-> outbox worker가 이벤트를 읽어 MongoDB Read Model 반영
```

중요한 점은 Outbox가 MongoDB 반영을 같은 트랜잭션에서 수행하기 위한 방식이 아니라는 것이다.

Outbox는 RDB 원본 변경과 이벤트 발생 사실을 같은 트랜잭션으로 보장하고, MongoDB 반영은 커밋 이후 별도 worker가 비동기로 처리하기 위한 방식이다.

요청 처리 흐름은 다음과 같이 둔다.

```text
사용자 요청
-> RDB 트랜잭션 시작
-> 댓글/기사/관심사/좋아요 등 원본 데이터 변경
-> 도메인 이벤트 수집
-> outbox_events 테이블에 이벤트 저장
-> RDB 커밋
-> 사용자 response 반환
-> outbox worker가 이벤트 조회
-> MongoDB Read Model 저장 또는 갱신
```

### Outbox 테이블 구조

Outbox 이벤트는 현재 프로젝트의 엔티티 ID 전략에 맞춰 JPA 엔티티로 관리하고, PK는 UUID를 사용한다.

Outbox 저장은 원본 엔티티 변경과 같은 RDB 트랜잭션에 참여해야 하므로 JPA Repository로 저장하는 것을 기본으로 한다.

```text
outbox_events

id              UUID PK
event_sequence  BIGINT NOT NULL
event_type      VARCHAR(80)
aggregate_type  VARCHAR(50)
aggregate_id    UUID
actor_user_id   UUID NULL
payload_json    JSON/JSONB
status          VARCHAR(20)
retry_count     INT
next_retry_at   TIMESTAMP NULL
occurred_at     TIMESTAMP
processed_at    TIMESTAMP NULL
last_error      TEXT NULL
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

`event_sequence`는 outbox 이벤트 간 비교 가능한 단조 증가 값으로 둔다. activity 상태 전이의 순서 보호 기준으로 사용하므로, 같은 activity key를 변경할 수 있는 이벤트에서는 `event_sequence` 순서가 RDB 최종 상태 순서와 어긋나면 안 된다.

DB sequence는 값의 할당 순서만 보장하며 transaction commit 순서를 보장하지 않는다. 따라서 후속 구현에서 DB sequence를 사용한다면 같은 `userId + type + targetType + targetId` activity key 또는 같은 activity 상태를 파생시키는 source aggregate를 먼저 직렬화한 뒤 `event_sequence`를 할당한다. 직렬화 수단은 transaction-scoped advisory lock, row lock, 또는 동등한 DB lock 전략을 후보로 두며, lock은 outbox row 저장과 `event_sequence` 할당 전 획득하고 RDB commit 또는 rollback까지 유지한다.

`source_version`은 바로 확정하지 않고 보류 컬럼으로 둔다.

```text
source_version  BIGINT NULL
```

이 컬럼은 원본 엔티티 snapshot 필드의 순서 역전 방지에는 유용하지만, 사용하려면 댓글, 기사, 관심사 같은 원본 엔티티에 version 필드를 먼저 추가해야 한다. 엔티티 변경 범위와 낙관적 락 예외 처리까지 함께 검토해야 하므로 초기 Outbox 기본 구조에는 포함하지 않는다.

각 컬럼의 의미는 다음과 같다.

```text
id
-> Outbox 이벤트 식별자
-> activity_histories 중복 방지를 위한 natural key는 아니다.
-> activity_histories 중복 문서 방지는 `userId + type + targetType + targetId` unique index와 atomic upsert로 보장한다.
-> snapshot 쓰기 멱등성은 commentId, articleId, interestId 같은 대상 ID 기준 upsert로 보장한다.
-> Outbox id 기준 처리 중복 확인이 필요하면 MongoDB 반영 이력 컬렉션 또는 RDB 처리 로그에 outbox id를 unique하게 기록하는 방식을 별도로 둔다.

event_sequence
-> Outbox 이벤트 간 비교 가능한 단조 증가 값
-> activity_histories의 visible, status, occurredAt 같은 상태 전이 순서 보호에 사용한다.
-> MongoDB activity에는 lastAppliedEventSequence로 마지막 반영 값을 저장한다.
-> source_version처럼 특정 원본 엔티티의 version이 아니라 projection 이벤트 전체의 적용 순서 기준이다.
-> 단순 DB sequence 할당 순서가 아니라 같은 activity key에 대해 RDB commit 순서와 어긋나지 않는 값이어야 한다.

event_type
-> 이벤트 종류
-> 예: COMMENT_WRITTEN, ARTICLE_VIEWED, INTEREST_UNSUBSCRIBED

aggregate_type
-> 원본 도메인 종류
-> 예: COMMENT, ARTICLE, INTEREST

aggregate_id
-> 원본 엔티티 ID

actor_user_id
-> 이벤트를 발생시킨 사용자 ID
-> 시스템 배치나 사용자 주체가 없는 이벤트는 NULL을 허용한다.

payload_json
-> MongoDB 문서가 아니라 도메인 이벤트 payload
-> 운영 DB가 PostgreSQL이면 JSONB, MySQL이면 JSON 사용을 우선한다.
-> 테스트 DB나 호환성 제약으로 JSON 타입 사용이 어렵다면 TEXT fallback을 허용한다.
-> TEXT fallback을 사용할 때는 애플리케이션에서 JSON 직렬화/역직렬화 검증을 수행한다.
-> worker가 event_type을 기준으로 해석해 MongoDB Read Model에 반영한다.

status
-> Outbox 처리 상태
-> PENDING, PROCESSED, FAILED, DEAD_LETTER를 기본 상태로 사용한다.

retry_count
-> worker 처리 실패 횟수

next_retry_at
-> FAILED 이벤트를 다시 처리할 수 있는 시각
-> PENDING 상태에서는 NULL을 허용한다.

occurred_at
-> 도메인 이벤트가 발생한 시각

processed_at
-> MongoDB Read Model 반영이 완료된 시각

last_error
-> 마지막 처리 실패 원인

created_at, updated_at
-> Outbox row 생성 및 수정 시각
```

초기 worker는 단일 worker 기준으로 단순하게 시작한다.

```text
PENDING 이벤트 또는 next_retry_at이 지난 FAILED 이벤트 조회
-> created_at ASC 순서로 처리
-> activity 또는 snapshot upsert 전에 이벤트가 참조하는 RDB source row 존재 여부 확인
-> source row가 물리삭제되어 없으면 MongoDB 문서를 생성하지 않고 stale cleanup event로 보고 PROCESSED 처리
-> activity_histories는 natural key 기준 atomic upsert로 중복 문서 생성 방지
-> activity 상태 전이는 직렬화된 event_sequence > lastAppliedEventSequence 조건을 만족할 때만 반영
-> occurredAt은 $max 또는 동등한 단조성 조건으로 갱신
-> *_activity_snapshots는 대상 ID 기준 upsert
-> 수정 가능한 snapshot 값은 오래된 payload로 덮어쓰지 않고 worker 처리 시점의 RDB 현재값을 조회해 반영
-> count 집계 이벤트 병합 처리 시 성공은 그룹 전체 PROCESSED, 실패 retry와 DEAD_LETTER 판정은 row별 처리
-> 오래된 event_sequence 재처리로 MongoDB update가 no-op이면 stale event로 보고 PROCESSED 처리
-> MongoDB Read Model 반영 성공 시 PROCESSED
-> 개별 이벤트 실패 시 FAILED, retry_count 증가, next_retry_at 설정, last_error 기록
-> 개별 이벤트가 최대 재시도 횟수를 초과하면 DEAD_LETTER로 전환
```

UUID는 순서 기준으로 사용하지 않는다. worker 조회와 처리 시도 순서는 `created_at` 기준으로 두되, activity 상태 반영 가능 여부는 직렬화된 `event_sequence` 기준으로 판단한다. `created_at`은 worker polling 편의를 위한 정렬 기준이지 transaction commit 순서 보장 기준이 아니다. `occurred_at`은 활동 발생 시각과 조회 정렬 기준이지 stale event 방지 기준으로 단독 사용하지 않는다.

물리삭제 cleanup 이후에는 기존 MongoDB activity 또는 snapshot 문서가 제거되어 `lastAppliedEventSequence`도 함께 사라질 수 있다. 이 경우 sequence guard만으로는 삭제 전 `PENDING` 또는 `FAILED` 이벤트의 재처리 upsert를 막을 수 없다. 따라서 worker는 natural key atomic upsert 또는 snapshot upsert 전에 RDB source row 존재 여부를 확인하고, source row가 없으면 payload만으로 activity나 snapshot을 복원하지 않는다. 이 이벤트는 이미 cleanup 이후 도착한 stale event로 보고 `PROCESSED` 처리한다.

후속 구현 검증에는 cleanup 이후 삭제 전 `PENDING` 또는 `FAILED` 이벤트를 재처리해도 해당 activity와 snapshot 문서가 다시 생성되지 않는 시나리오를 포함한다.

count 집계 이벤트를 batch 안에서 병합 처리하는 경우 상태 전이는 outbox row 단위가 아니라 선택된 그룹 row 전체에 적용한다. 그룹 기준은 현재 polling batch에서 선택된 row 중 같은 `event_type`과 같은 snapshot 대상 ID를 가진 row다.

```text
count 집계 이벤트 그룹 처리
-> 선택된 row를 event_type + 대상 ID 기준으로 그룹화
-> 그룹별로 RDB 현재 집계값 조회
-> MongoDB snapshot을 대상 ID 기준 upsert 후 현재 집계값으로 $set
-> MongoDB 반영 성공 후 선택된 그룹 row 전체를 PROCESSED, 동일 processed_at으로 bulk update
-> MongoDB 반영 실패를 감지하면 선택된 그룹 row 각각의 retry_count를 1 증가
-> 증가 후 retry_count >= max_retry_count인 row는 DEAD_LETTER 처리
-> 아직 한도 미만인 row는 FAILED 처리하고 row별 retry_count 기준으로 next_retry_at 설정
-> last_error는 실패한 그룹 row 전체에 같은 원인을 기록
```

병합 그룹에서는 대표 row만 `PROCESSED`로 변경하지 않는다. 나머지 row가 `PENDING` 또는 재시도 가능한 `FAILED`로 남으면 같은 신호가 다시 선택될 수 있기 때문이다. 반대로 MongoDB 반영 전에 그룹 row를 먼저 `PROCESSED`로 변경하지 않는다. 반영 실패 시 count 변경 신호가 유실될 수 있기 때문이다. 성공 상태 전이는 그룹 전체에 동일하게 적용하지만, 실패 시 `retry_count`, `next_retry_at`, `DEAD_LETTER` 판정은 row별 기존 retry 이력을 보존해 계산한다.

MongoDB 반영 성공 후 outbox 상태 저장 전에 worker가 중단되면 그룹 row가 다시 선택될 수 있다. 이 경우 snapshot 대상 ID 기준 upsert와 RDB 현재 집계값 `$set`으로 재처리가 멱등하게 수렴해야 한다. MongoDB 반영 실패 후 `FAILED` 저장 전 중단된 경우에도 기존 `PENDING` 또는 `FAILED` 상태가 남아 그룹 전체가 다시 재시도된다.

초기 재시도 정책은 다음과 같이 둔다.

```text
max_retry_count = 5

1회 실패 후 next_retry_at = now + 1분
2회 실패 후 next_retry_at = now + 5분
3회 실패 후 next_retry_at = now + 15분
4회 실패 후 next_retry_at = now + 1시간
5회 실패 시 DEAD_LETTER
```

`DEAD_LETTER` 이벤트는 worker가 자동 재처리하지 않는다. 운영자가 `last_error`와 원본 데이터를 확인한 뒤 수동으로 상태를 `PENDING`으로 되돌리거나 별도 보정 작업으로 처리한다.

worker 조회를 위한 권장 인덱스는 다음과 같다.

```text
status, next_retry_at, created_at
```

worker 조회 조건은 상태와 처리 가능 시각을 기준으로 둔다. payload 내부 필드를 조회 조건으로 사용하지 않는 한 별도 JSON path/index는 만들지 않는다.

여러 worker를 동시에 운영해야 하는 경우에는 같은 이벤트를 여러 worker가 동시에 처리하지 않도록 JPQL/native query 또는 DB lock 전략을 별도로 검토한다.

### Activity 상태 순서 보호 기준

`userId + type + targetType + targetId` natural key와 atomic upsert는 같은 activity 문서가 중복 생성되지 않도록 막는다. 하지만 이전 이벤트가 실패했다가 나중에 재처리되는 경우, natural key만으로는 최신 activity 상태를 오래된 상태로 되돌리는 문제를 막을 수 없다.

```text
E1: 댓글 좋아요
E2: 좋아요 취소

E1 처리 실패
-> E2 처리 성공, activity visible=false, status=CANCELED
-> E1 재시도
```

위 순서에서 E1이 단순 upsert로 재처리되면 activity가 다시 `ACTIVE`가 될 수 있다. 따라서 activity 상태 전이는 다음 조건부 update로 보호한다.

```text
update 조건
-> natural key 일치
-> lastAppliedEventSequence가 없거나 lastAppliedEventSequence < 현재 event_sequence

update 내용
-> visible, status, hiddenByTargetType, hiddenByTargetId 등 상태 필드 갱신
-> occurredAt은 $max 또는 동등한 단조 조건으로만 갱신
-> lastAppliedEventSequence = 현재 event_sequence
```

조건을 만족하지 않는 이벤트는 이미 더 최신 이벤트가 반영된 stale event로 본다. 이 경우 MongoDB update는 no-op이며, outbox row는 재시도 대상이 아니라 처리 완료로 볼 수 있다.

이 기준은 JPA `@Version` 낙관적 락과 유사한 목적을 갖지만, RDB 원본 엔티티의 동시 수정 충돌 제어가 아니라 MongoDB Read Model projection의 오래된 이벤트 재처리 방지 조건이다.

다만 이 guard가 올바르게 동작하려면 같은 activity key에 대한 `event_sequence`가 RDB 최종 상태 순서와 같은 방향이어야 한다. 아래 순서가 가능하면 낮은 sequence 이벤트가 나중 commit되더라도 stale event로 버려질 수 있다.

```text
T1: COMMENT_LIKED, event_sequence=101 할당
T2: COMMENT_LIKE_CANCELED, event_sequence=102 할당
T2 먼저 commit
-> worker가 102 반영, lastAppliedEventSequence=102
T1 나중 commit
-> worker가 101을 stale event로 보고 no-op
```

후속 구현에서는 같은 activity key를 변경할 수 있는 트랜잭션이 위 순서로 commit되지 않도록 projection key 기준 직렬화를 적용한다. 직렬화가 어려운 이벤트는 payload의 상태값을 그대로 반영하지 않고 worker가 RDB 현재 상태를 재조회해 MongoDB Read Model을 수렴시키는 방식으로 별도 설계한다.

후속 구현 검증에는 같은 activity key에 대해 낮은 `event_sequence`를 먼저 할당한 트랜잭션과 높은 `event_sequence`를 나중에 할당한 트랜잭션의 commit 순서가 역전되는 시나리오를 포함한다. 직렬화 적용 후에는 나중 commit된 이벤트가 낮은 sequence라는 이유로 stale 처리되지 않아야 하며, 최종 MongoDB activity 상태가 RDB 기준 최종 상태와 일치해야 한다.

### Source Version 검토 기준

`source_version`은 원본 엔티티 변경 이벤트가 순서 역전으로 MongoDB snapshot을 잘못 덮어쓰는 문제를 줄이기 위한 값이다.

하지만 현재 엔티티에 version 필드가 없다면 Outbox에 `source_version`만 추가해도 의미가 약하다. 따라서 다음 항목을 별도로 검토한 뒤 도입 여부를 결정한다.

```text
- 댓글 수정/삭제 이벤트가 순서 역전으로 MongoDB snapshot을 잘못 덮어쓸 위험이 큰지
- 기사 수정/삭제 이벤트에도 같은 문제가 있는지
- 관심사 키워드 변경/삭제 이벤트에도 version 기준이 필요한지
- JPA @Version 도입이 기존 수정 로직에 영향을 주는지
- 낙관적 락 예외 처리를 프로젝트 범위에서 감당할 수 있는지
```

초기 구현에서는 `source_version` 없이 Outbox 이벤트를 저장하고, 단일 worker가 `created_at ASC` 기준으로 처리한다. 다만 activity 상태 전이는 `source_version`이 아니라 직렬화된 `event_sequence`와 `lastAppliedEventSequence` 비교로 보호한다.

다만 `source_version`이 없는 동안에도 재시도 중인 이전 이벤트가 최신 snapshot을 덮어쓰면 안 된다. 따라서 댓글 내용, 기사 제목/요약/게시일, 관심사 키워드, count 집계값처럼 나중 이벤트로 변경될 수 있는 snapshot 필드는 event payload의 표시값을 그대로 최종값으로 쓰지 않는다.

```text
E1 처리 실패
-> E2 처리 성공
-> E1 재시도
```

위 순서가 발생해도 worker는 E1 payload의 오래된 표시값을 덮어쓰지 않고, 처리 시점의 RDB 현재값을 다시 조회해 snapshot에 `$set`한다. 같은 aggregate의 후속 이벤트 보류나 `source_version` guard는 원본 엔티티 snapshot 필드 보호가 필요하고 엔티티 version 도입이 확정될 때 선택할 수 있는 대안으로 둔다.

`DEAD_LETTER`로 전환된 이벤트를 수동 재처리할 때도 같은 기준을 적용한다. 즉, 재처리 이벤트는 payload에 있던 과거 표시값을 복원하지 않고, RDB 현재값 기준으로 MongoDB Read Model을 수렴시킨다.

### 대안과 트레이드오프

```text
1. 같은 트랜잭션에서 원본 변경 + outbox 이벤트 저장

장점
- RDB 변경과 이벤트 저장이 함께 보장된다.
- 서버 장애가 발생해도 커밋된 이벤트를 나중에 재처리할 수 있다.
- MongoDB Read Model 누락을 복구할 근거가 남는다.

단점
- 사용자 응답 전에 outbox insert 1회가 추가된다.

판단
- 후속 적용 시 정합성 근거가 필요하므로 기본안 후보로 둔다.
```

```text
2. 원본 변경 후 이벤트 저장도 비동기로 처리

장점
- 사용자 response 영향이 가장 적다.

단점
- response 반환 후 이벤트 저장 전에 서버 장애가 발생하면 이벤트가 유실될 수 있다.
- RDB에는 데이터가 있지만 MongoDB 활동내역에는 영원히 반영되지 않을 수 있다.

판단
- 이벤트 유실 가능성을 설명하기 어렵기 때문에 기본안으로 사용하지 않는다.
```

```text
3. MongoDB까지 요청 흐름에서 동기 저장

장점
- MongoDB Read Model이 즉시 반영된다.

단점
- 사용자 응답 시간이 늘어난다.
- RDB와 MongoDB 중 하나만 성공하는 dual-write 문제가 생긴다.
- 실패 보상 로직이 복잡해진다.

판단
- Outbox의 목적과 맞지 않으므로 기본안에서 제외한다.
```
