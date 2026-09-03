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
claim_id        UUID NULL
claimed_at      TIMESTAMP NULL
claim_until     TIMESTAMP NULL
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

위 구조는 후속 구현이 모두 완료됐을 때의 최종 목표다. 실제 적용은 티켓 의존 순서에 맞춰 나눈다.

```text
MID4-136
-> event_type, aggregate 정보, payload_json, 상태, retry, 처리 시각을 포함한 기본 outbox_events 저장 구조
-> OutboxEvent JPA 엔티티와 repository

MID4-246
-> 이벤트별 payload 사용 필드와 RDB 재조회 필드 분류
-> 대상 ID별 RDB 현재 상태 batch 재조회와 수렴 정책
-> 중복, 재시도, commit 순서 역전, 물리삭제 후 지연 이벤트 시나리오 검증 기준

MID4-137
-> Outbox event/aggregate/action enum과 타입별 payload record
-> 기존 RDB 트랜잭션에 참여하는 OutboxEventWriter와 JSON 직렬화 실패 예외
-> 사용자·관심사·기사·댓글 변경 흐름의 Outbox producer 연동

MID4-138
-> PENDING과 재시도 시각이 지난 FAILED 이벤트를 SKIP LOCKED와 lease로 batch claim하는 병렬 worker
-> event type별 대상 ID 집합을 이용한 RDB 현재 상태 batch 재조회
-> MongoDB activity/snapshot atomic upsert, 숨김, cleanup과 source row 부재 시 no-op
-> row별 retry와 DEAD_LETTER 상태 전이
```

MID4-246은 Outbox 테이블 컬럼이나 애플리케이션 코드를 추가하지 않고 payload 책임과 worker 재조회 책임을 먼저 확정했다. MID4-137은 이 정책에 맞춰 실제 도메인 쓰기 흐름이 Outbox row를 생성하도록 producer를 연동했다.

초기 구현에는 `event_sequence`, projection key, advisory lock, 낙관적 락을 추가하지 않는다. DB sequence는 값 할당 순서와 transaction commit 순서가 일치하지 않으므로, 정확한 순서 guard로 쓰려면 producer 쓰기 경로를 직렬화해야 한다. 이 추가 락은 request transaction의 대기 시간과 DB connection 점유에 영향을 줄 수 있어 현재 범위에서 선택하지 않는다. 다중 consumer가 이벤트를 선점하는 짧은 transaction에만 PostgreSQL row lock인 `FOR UPDATE SKIP LOCKED`를 사용하고, MongoDB 처리 동안 RDB lock이나 transaction을 유지하지 않는다.

대신 Outbox 이벤트를 MongoDB에 적용할 상태 snapshot이 아니라 현재 상태 재계산 신호로 취급한다. 단일 worker의 순차 처리와 재시도에서는 같은 batch의 대상 ID를 모아 RDB 현재 상태를 조회하고 이를 MongoDB에 멱등 반영해, 이벤트 처리 순서가 바뀌어도 최종 상태를 RDB에 수렴시킨다. 여러 worker의 동일 target 동시 쓰기는 별도 순서 보호가 없으므로 아래 제한을 따른다.

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

event_type
-> 이벤트 종류
-> 예: COMMENT_WRITTEN, ARTICLE_VIEWED, INTEREST_UNSUBSCRIBED

aggregate_type
-> 원본 도메인 종류
-> 예: COMMENT, ARTICLE, INTEREST

MID4-136의 기본 저장 모델에서는 `event_type`과 `aggregate_type`을 Java `String`으로 두었다. MID4-137에서 실제 저장할 이벤트 목록을 확정하고 `OutboxEventType`, `OutboxAggregateType` enum을 추가해 JPA `EnumType.STRING`으로 전환했다. DB 컬럼은 PostgreSQL 전용 enum으로 바꾸지 않고 기존 `VARCHAR(80)`, `VARCHAR(50)`을 유지하므로 컬럼 길이 안의 Java enum 값 추가는 DB enum migration을 요구하지 않는다.

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
-> event ID, event type, aggregate type/ID, actor user ID, occurredAt은 각각 Outbox row의 공통 envelope 컬럼에 저장하고 payload에 중복하지 않는다.
-> payload에는 부모 대상 ID, action/reason, 삭제 전 확보한 불변 영향 대상 ID처럼 이벤트별 처리에 필요한 body만 담는다.
-> 댓글 작성 시각처럼 불변인 값은 payload snapshot을 사용할 수 있다.
-> content, title, keywords, visible, 좋아요·구독 활성 여부, count처럼 바뀔 수 있는 값은 포함할 수 있어도 MongoDB 최종값으로 신뢰하지 않는다.
-> worker는 event_type과 ID를 기준으로 대상을 묶고 RDB 현재 상태를 batch 조회해 MongoDB Read Model에 반영한다.

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

claim_id
-> 한 polling batch의 실행 UUID

claimed_at, claim_until
-> PostgreSQL 시각 기준 claim 시작과 lease 만료 시각
-> heartbeat가 claim_until을 연장하고, 만료되면 다른 worker가 새 claim_id로 회수한다.
```

### MID4-137 producer 저장 계약

producer는 다음 애플리케이션 인터페이스로 Outbox 이벤트를 저장한다.

```text
OutboxEventWriter.write(
  OutboxEventType eventType,
  OutboxAggregateType aggregateType,
  UUID aggregateId,
  UUID actorUserId,        // nullable
  OutboxEventPayload payload
)
```

`OutboxEventPayload`는 도메인별 record만 허용하는 sealed interface다. producer는 record를 넘기고, `OutboxPayloadSerializer`가 Jackson `ObjectMapper.valueToTree`로 `JsonNode`를 만든 뒤 `payload_json` JSONB 컬럼에 저장한다. `OutboxEventAction` enum은 JSON 안에서 `WRITTEN`, `UPDATED`, `COUNT_CHANGED` 같은 문자열로 직렬화된다.

`OutboxEventWriter.write`는 `Propagation.MANDATORY`를 사용한다. 따라서 호출한 도메인 서비스의 기존 트랜잭션에 참여하고, 기존 트랜잭션이 없으면 `IllegalTransactionStateException`으로 거부한다. `REQUIRES_NEW` helper에서 호출하면 그 helper가 시작한 새 트랜잭션에 참여한다. writer가 별도 트랜잭션을 만들거나 독립 커밋하지 않는다.

payload 직렬화와 Outbox 저장은 요청 트랜잭션 안에서 동기 수행된다. 직렬화가 실패하면 `OutboxPayloadSerializationException`이 발생하고 원본 변경과 Outbox row가 함께 롤백된다. 비동기 범위는 커밋 이후 향후 worker가 수행할 MongoDB 반영이며, MID4-137에는 worker가 포함되지 않는다.

MID4-137에서 저장하는 이벤트 계약은 다음과 같다. `aggregate_id`와 `actor_user_id`는 공통 envelope 컬럼이므로 아래 payload body에 중복하지 않는다.

| event_type | aggregate_type | actor_user_id | payload_json body |
| --- | --- | --- | --- |
| `INTEREST_SUBSCRIBED` | `INTEREST` | 구독 사용자 | `{"action":"SUBSCRIBED"}` |
| `INTEREST_UNSUBSCRIBED` | `INTEREST` | 구독 해제 사용자 | `{"action":"UNSUBSCRIBED"}` |
| `COMMENT_WRITTEN` | `COMMENT` | 댓글 작성자 | `{"articleId":"...","action":"WRITTEN"}` |
| `COMMENT_LIKED` | `COMMENT` | 좋아요 사용자 | `{"articleId":"...","action":"LIKED"}` |
| `COMMENT_LIKE_CANCELED` | `COMMENT` | 좋아요 취소 사용자 | `{"articleId":"...","action":"LIKE_CANCELED"}` |
| `ARTICLE_VIEWED` | `ARTICLE` | 조회 사용자 | 최초 조회는 `{"action":"VIEWED"}`, 재조회 시각 갱신은 `{"action":"TOUCHED"}` |
| `INTEREST_UPDATED` | `INTEREST` | `NULL` | `{"action":"UPDATED"}` |
| `INTEREST_HARD_DELETED` | `INTEREST` | `NULL` | `{"action":"HARD_DELETED"}` |
| `COMMENT_UPDATED` | `COMMENT` | 수정 요청 사용자 | `{"articleId":"...","action":"UPDATED"}` |
| `COMMENT_SOFT_DELETED` | `COMMENT` | `NULL` | `{"articleId":"...","action":"SOFT_DELETED"}` |
| `COMMENT_HARD_DELETED` | `COMMENT` | `NULL` | `{"articleId":"...","action":"HARD_DELETED"}` |
| `ARTICLE_SOFT_DELETED` | `ARTICLE` | `NULL` | `{"action":"SOFT_DELETED"}` |
| `ARTICLE_HARD_DELETED` | `ARTICLE` | `NULL` | `{"action":"HARD_DELETED"}` |
| `USER_NICKNAME_UPDATED` | `USER` | 변경 사용자 | `{"action":"UPDATED"}` |
| `USER_SOFT_DELETED` | `USER` | 삭제 사용자 | `{"action":"SOFT_DELETED"}` |
| `USER_HARD_DELETED` | `USER` | `NULL` | `action=HARD_DELETED`와 `authoredCommentIds`, `impactedArticleIds`, `likedCommentIds`, `viewedArticleIds`, `subscribedInterestIds` |
| `INTEREST_SUBSCRIBER_COUNT_CHANGED` | `INTEREST` | 구독 또는 해제 사용자 | `{"action":"COUNT_CHANGED"}` |
| `COMMENT_LIKE_CHANGED` | `COMMENT` | 좋아요 또는 취소 사용자 | `{"action":"COUNT_CHANGED"}` |
| `ARTICLE_VIEW_COUNT_CHANGED` | `ARTICLE` | 조회 사용자 | `{"action":"COUNT_CHANGED"}` |
| `ARTICLE_COMMENT_COUNT_CHANGED` | `ARTICLE` | 댓글 작성자는 사용자 ID, 삭제는 `NULL` | `{"action":"COUNT_CHANGED"}` |

사용자 물리삭제 payload의 영향 ID 목록은 연관 row를 삭제하기 전에 수집하고 중복을 제거한, 해당 transaction이 수집 시점에 관찰한 불변 snapshot이다. 초기 구현은 producer 락을 사용하지 않으므로 수집 이후 연관 row 삭제 전에 다른 transaction이 commit한 관계까지 포함하는 선형화 가능한 전체 목록은 아니다. 이는 worker의 cleanup 후보 탐색에 사용하기 위한 정보이며, 삭제된 원본이나 MongoDB 문서를 복원하는 근거나 유일한 cleanup 대상 목록으로 사용하지 않는다.

MID4-138 worker는 `USER_HARD_DELETED`의 `aggregate_id`로 해당 사용자의 `activity_histories`를 제거하고, payload 영향 ID를 snapshot 제거와 count 재계산 후보로 사용한다. 사용자 물리삭제 이후 처리되는 활동 이벤트는 actor 사용자와 source row의 RDB 존재 여부를 다시 확인해 새 activity 또는 snapshot을 만들지 않는다. 수집과 삭제 사이의 동시 관계 쓰기로 payload에서 누락될 수 있는 대상은 producer 락을 추가하지 않은 현재 정책의 제한이며, 후속 stale replay 동시성 검증 범위로 남긴다.

상태 전이는 애플리케이션 도메인 모델에서 검증한다. `PENDING`과 `FAILED`에서만 처리 성공, 처리 실패, `DEAD_LETTER` 전환을 허용하고, `PROCESSED`와 `DEAD_LETTER`는 일반 worker 처리에서 변경할 수 없는 종결 상태로 취급한다. 허용되지 않은 전이는 Outbox 전용 커스텀 예외로 거부하며 상태와 재시도 메타데이터를 변경하지 않는다. 이 규칙은 애플리케이션 계층에서 관리하고 DB `CHECK` 제약은 추가하지 않는다.

운영자 수동 재처리를 위한 종결 상태 초기화 기능은 MID4-136에 포함하지 않으며 MID4-251에서 별도로 정의한다.

worker는 여러 인스턴스에서 서로 다른 batch를 병렬 처리할 수 있다.

```text
PENDING 이벤트 또는 next_retry_at이 지난 FAILED 이벤트 후보를 created_at, id 순서로 조회
-> 짧은 claim transaction에서 FOR UPDATE SKIP LOCKED 적용
-> batch UUID와 claimed_at, claim_until을 원자적으로 기록한 뒤 transaction 종료
-> 처리 중 heartbeat로 lease 연장
-> created_at ASC 순서로 처리
-> event_type별 target ID를 추출하고 같은 대상의 source row, 관계 활성 상태, 표시값과 count를 batch 조회
-> source row가 물리삭제되어 없으면 이벤트 매핑에 따라 기존 MongoDB 문서를 숨기거나 삭제하고 payload만으로 새 문서를 생성하지 않음
-> activity_histories는 natural key 기준 atomic upsert로 중복 문서 생성 방지
-> activity의 visible, status는 좋아요·구독 row 존재 여부와 대상·부모의 현재 노출 상태로 계산
-> occurredAt은 현재 관계 row의 시각 또는 검증된 불변 이벤트 시각을 사용하고, 같은 활동의 시각 갱신은 $max 또는 동등한 단조 조건 적용
-> *_activity_snapshots는 대상 ID 기준 upsert
-> 수정 가능한 snapshot 값은 오래된 payload로 덮어쓰지 않고 batch 조회한 RDB 현재값을 반영
-> MID4-138에서는 count 이벤트도 RDB 현재값으로 개별 반영
-> MID4-247에서 같은 polling batch의 count 이벤트를 그룹화하고 그룹 전체 상태 전이로 확장
-> 현재 상태를 이미 반영한 중복·지연 이벤트도 멱등 처리 후 PROCESSED
-> MongoDB Read Model 반영 성공 시 PROCESSED
-> 개별 이벤트 실패 시 FAILED, retry_count 증가, next_retry_at 설정, last_error 기록
-> 개별 이벤트가 최대 재시도 횟수를 초과하면 DEAD_LETTER로 전환
```

UUID는 순서 기준으로 사용하지 않는다. worker 조회와 처리 시도 순서는 `created_at` 기준으로 두지만 이는 polling 편의를 위한 정렬일 뿐 transaction commit 순서나 projection 정확성 보장 기준이 아니다. `occurred_at`도 활동 발생 시각과 조회 정렬 정보이며 stale event 판정에 단독 사용하지 않는다. 정확성은 처리 시점의 RDB 현재 상태 재조회로 보장한다.

물리삭제 cleanup 이후 삭제 전 `PENDING` 또는 `FAILED` 이벤트가 재처리될 수 있다. 따라서 worker는 natural key atomic upsert 또는 snapshot upsert 전에 RDB source row 존재 여부를 확인하고, source row가 없으면 payload만으로 activity나 snapshot을 복원하지 않는다. 필요한 숨김·삭제가 이미 반영됐다면 no-op 후 `PROCESSED` 처리한다.

후속 구현 검증에는 cleanup 이후 삭제 전 `PENDING` 또는 `FAILED` 이벤트를 재처리해도 해당 activity와 snapshot 문서가 다시 생성되지 않는 시나리오를 포함한다.

MID4-138에서는 count 이벤트도 다른 이벤트와 동일하게 row별 projection과 상태 전이를 수행한다. 같은 batch에서 같은 대상 ID가 반복되면 RDB count query는 ID 집합으로 묶이지만, MongoDB upsert와 `PROCESSED` 또는 실패 상태 저장은 이벤트 row마다 실행한다.

```text
E1: ARTICLE_VIEW_COUNT_CHANGED + articleId=A1
E2: ARTICLE_VIEW_COUNT_CHANGED + articleId=A1

MID4-138
-> RDB article A1의 현재 count를 batch query로 조회
-> E1 projection 후 E1을 PROCESSED
-> E2 projection 후 E2를 PROCESSED

MID4-247 후속 범위
-> E1과 E2를 같은 그룹으로 병합
-> MongoDB projection 1회 후 그룹 row 전체 상태 전이
```

count 집계 이벤트를 batch 안에서 병합 처리하는 후속 구현에서는 상태 전이를 outbox row 단위가 아니라 선택된 그룹 row 전체에 적용한다. 그룹 기준은 현재 polling batch에서 선택된 row 중 같은 `event_type`과 같은 snapshot 대상 ID를 가진 row다.

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

### MID4-138 실패 경계와 오류 코드

worker는 모든 실패를 같은 방식으로 처리하지 않는다. MongoDB projection을 시작하지 못한 batch와 개별 이벤트 처리 실패, 상태 저장 실패를 구분한다.

| 실패 단계 | MID4-138 동작 | claim 처리 |
| --- | --- | --- |
| claim transaction | 예외를 호출자에게 전파하며 transaction을 롤백한다. | 새 claim이 남지 않는다. |
| heartbeat scheduler 등록 | 오류를 기록하고 batch 처리를 시작하지 않는다. | 즉시 release하며, release도 실패하면 lease 만료를 기다린다. |
| payload decode | 해당 이벤트를 실패 처리하고 다음 이벤트 decode를 계속한다. | 실패 상태 저장 시 해당 row의 claim을 해제한다. |
| RDB source batch 조회 | decode된 이벤트를 각각 실패 처리한다. | 상태 저장에 실패한 지점부터 처리를 중단하고 남은 claim은 만료시킨다. |
| MongoDB projection | 해당 이벤트를 실패 처리하고 다음 이벤트를 계속한다. | 실패 상태 저장 시 해당 row의 claim을 해제한다. |
| `PROCESSED` 또는 실패 상태 저장 | 새 이벤트 처리를 중단한다. | 종결하지 못한 row는 lease 만료 후 재선점될 수 있다. |
| heartbeat 갱신 또는 소유권 확인 | 다음 처리 경계에서 batch 처리를 중단한다. | 남은 claim을 명시적으로 해제하지 않고 만료시킨다. |

```text
heartbeat 시작 실패
-> OutboxHeartbeatStartException(OBX_007) 로그
-> claim_id 기준 즉시 release 시도
-> MongoDB projection 시작 안 함

heartbeat DB 갱신 실패
-> lease handle에 OBX_008 기록
-> worker가 다음 decode/projection 경계에서 감지
-> 새 이벤트 처리 중단
-> 아직 종결하지 않은 row는 claim_until 만료 후 다른 worker가 회수

MongoDB 반영 성공 후 PROCESSED 저장 실패
-> MongoDB에는 변경이 반영됐지만 Outbox row는 미종결
-> lease 만료 후 재처리될 수 있으므로 projection 멱등성 필요
```

`last_error`는 실패 예외의 단순 클래스 이름과 메시지를 저장하며 최대 4,000자로 자른다. 실패 상태 저장이 성공하면 `retry_count`를 증가시키고 claim 필드를 정리한다.

| 코드 | 예외 | 발생 조건 | details |
| --- | --- | --- | --- |
| `OBX_003` | `OutboxPayloadDeserializationException` | JSON payload를 event type의 record로 복원하지 못함 | `payloadType` |
| `OBX_004` | `OutboxClaimOwnershipLostException` | 상태 갱신 또는 heartbeat 대상 claim이 더 이상 없음 | `eventId`(선택), `claimId` |
| `OBX_005` | `OutboxWorkerConfigurationException` | polling, batch, lease, heartbeat 설정이 유효하지 않음 | `property`, `rejectedValue`, `reason` |
| `OBX_006` | `OutboxRetryPolicyException` | 한도를 소진한 retry count에 다음 지연시간을 요청함 | `currentRetryCount`, `maxRetryCount` |
| `OBX_007` | `OutboxHeartbeatStartException` | heartbeat scheduler 등록 실패 | `claimId` |
| `OBX_008` | `OutboxHeartbeatRenewException` | heartbeat DB 갱신 실패 | `claimId` |

이 예외들은 worker 내부 상태 전이, 로그와 `last_error` 진단을 위한 계약이며 별도 Outbox HTTP endpoint의 응답 계약을 의미하지 않는다.

```text
payload decode 첫 실패 예시
status=PENDING, retry_count=0
-> status=FAILED, retry_count=1
-> next_retry_at=failed_at+1분
-> last_error="OutboxPayloadDeserializationException: Outbox 이벤트 payload 역직렬화에 실패했습니다."
-> claim_id, claimed_at, claim_until=NULL
```

### Worker 조회 인덱스 정책

MID4-138에서는 worker polling query용 인덱스를 추가하지 않고 실행계획이나 성능 측정도 수행하지 않는다. 운영과 유사한 상태별 row 분포에서 polling이 실제 병목으로 확인되는 경우에만 별도 티켓에서 인덱스를 검토한다.

```text
인덱스 후보
-> status, next_retry_at, created_at

추가 조건
-> 실제 worker polling query가 확정됨
-> 운영과 유사한 상태별 row 분포로 실행계획을 측정함
-> 인덱스가 쓰기 비용보다 큰 조회 개선을 제공함
```

worker 조회 조건은 상태와 처리 가능 시각을 기준으로 두되, 측정 전에는 해당 컬럼 인덱스도 만들지 않는다. payload 내부 필드를 조회 조건으로 사용하지 않는 한 JSON path/index 역시 만들지 않는다.

### 다중 worker 실행과 동일 target 제한

각 worker instance는 자신이 claim한 polling batch의 이벤트를 순차 실행하고, blocking 방식의 RDB 조회와 MongoDB 쓰기가 완료된 뒤 다음 이벤트를 처리한다. 요청 처리와는 분리된 비동기 worker지만 worker 내부 I/O가 reactive/non-blocking이라는 의미는 아니다. 서로 다른 인스턴스의 batch는 병렬 처리될 수 있다.

모든 성공·실패 상태 갱신은 `event_id + claim_id`가 일치할 때만 수행해 lease 만료 후 이전 worker가 새 소유자의 Outbox 상태를 덮어쓰지 못하게 한다. 다만 서로 다른 이벤트 row가 같은 target을 참조할 수 있으므로 claim UUID는 target별 MongoDB 쓰기를 직렬화하지 않는다. natural key는 중복 문서를 막고 `$max`는 `occurredAt` 역행을 막지만, snapshot과 activity의 `$set` 필드에는 순서 guard가 없다.

```text
W1 batch: COMMENT_UPDATED C1 -> RDB에서 content="old" 조회
W2 batch: COMMENT_UPDATED C1 -> RDB에서 content="new" 조회
W2가 "new"를 먼저 $set
W1이 "old"를 나중에 $set
-> 문서 중복은 없지만 content는 old로 회귀할 수 있음
```

따라서 MID4-138은 이벤트 row 소유권과 at-least-once 재처리는 보호하지만, 다중 instance의 동일 target concurrent projection까지 완전한 수렴을 보장하지 않는다. target별 직렬화, source version, fencing token과 삭제 tombstone은 현재 범위에 포함하지 않는다.

MID4-138 worker는 기본 비활성화한다. `MONEW_MONGODB_ENABLED=true`와 `MONEW_MONGODB_WORKER_ENABLED=true`를 설정한 여러 인스턴스에서 실행할 수 있다. polling 간격과 batch 크기는 각각 `MONEW_MONGODB_WORKER_FIXED_DELAY_MS`, `MONEW_MONGODB_WORKER_BATCH_SIZE`로 조정하고 초기 기본값은 1초와 100건이다. `MONEW_MONGODB_WORKER_CLAIM_LEASE` 기본값은 5분, `MONEW_MONGODB_WORKER_HEARTBEAT_INTERVAL` 기본값은 1분이며 heartbeat 간격은 lease보다 짧아야 한다.

### Activity 현재 상태 수렴 기준

`userId + type + targetType + targetId` natural key와 atomic upsert는 같은 activity 문서가 중복 생성되지 않도록 막는다. 여기에 worker의 RDB 현재 상태 재조회를 결합해 이전 이벤트가 실패했다가 나중에 재처리되어도 payload의 오래된 상태로 되돌아가지 않게 한다.

```text
E1: 댓글 좋아요
E2: 좋아요 취소

E1 처리 실패
-> E2 처리 성공 후 RDB 좋아요 row 없음
-> E1 재시도 시에도 RDB 좋아요 row 없음으로 조회
-> activity visible=false, status=CANCELED 유지
```

worker는 이벤트의 `COMMENT_LIKED` 또는 `COMMENT_LIKE_CANCELED` 이름만으로 최종 상태를 정하지 않는다. 두 이벤트 모두 사용자 ID와 댓글 ID로 현재 좋아요 row 존재 여부, actor 사용자의 존재·논리삭제 상태, 댓글과 부모 기사의 노출 여부를 조회하고 같은 계산 규칙을 적용한다.

```text
좋아요 row 존재 + 댓글/기사 노출 가능
-> visible=true, status=ACTIVE
-> occurredAt은 현재 좋아요 row의 생성 시각 기준으로 단조 갱신

좋아요 row 없음
-> visible=false, status=CANCELED

좋아요 row 존재 + 댓글 또는 기사 노출 불가
-> visible=false, status=TARGET_DELETED

actor 사용자 논리삭제 또는 물리삭제
-> 새 activity upsert 금지
-> 기존 activity가 있으면 visible=false, status=USER_DELETED 유지
```

구독도 같은 방식으로 현재 구독 row와 관심사 노출 상태를 조회한다. 댓글 작성, 기사 조회와 대상 수정·삭제 이벤트도 source row와 부모 대상의 현재 상태를 조회해 activity와 snapshot을 계산한다. 복구·재노출 이벤트는 아직 구현하지 않았으며 후속 구현에서도 같은 현재 상태 재계산 원칙을 적용한다.

같은 polling batch에서는 이벤트 row마다 같은 source를 반복 조회하지 않는다. event type을 처리 규칙으로 매핑한 뒤 실제 RDB 조회는 commentId, articleId, interestId, userId 같은 target ID 집합으로 묶는다. MongoDB 반영 성공 여부와 Outbox 상태 전이 규칙은 기존 batch 정책을 그대로 따른다.

commit 순서가 이벤트 생성 시도 순서와 역전되는 경우도 별도 sequence 없이 다음과 같이 수렴한다.

```text
T1: 댓글 좋아요 transaction 시작 및 Outbox row 준비
T2: 좋아요 취소 transaction 시작 및 Outbox row 준비
T2 먼저 commit
-> worker가 RDB 현재 상태를 조회해 취소 상태 반영
T1 나중 commit
-> T1 Outbox 이벤트 처리 시 RDB 현재 상태를 다시 조회
-> 최종 commit된 RDB 상태를 MongoDB에 반영
```

worker가 두 commit 사이에 실행되면 일시적으로 먼저 commit된 상태가 반영될 수 있다. 하지만 나중 commit된 transaction에도 Outbox row가 있으므로 후속 처리에서 RDB 현재 상태를 다시 읽어 최종 상태로 수렴한다. 중복 처리와 재시도 역시 같은 계산을 반복하므로 멱등하다.

MID4-138 테스트는 서로 다른 worker의 claim batch가 겹치지 않는지, 만료 claim 회수, 이전 claim UUID의 상태 갱신 차단, row별 retry와 heartbeat 갱신을 검증한다. 단일 worker 흐름에서는 지연된 좋아요 이벤트가 현재 관계 상태를 반영하는지와 삭제된 actor의 activity를 숨기는지도 검증한다. 서로 다른 worker가 같은 target을 동시에 projection하는 interleaving, 사용자 삭제와 stale activity upsert 경쟁, cleanup 이후 삭제 전 이벤트 재생성 차단은 아직 후속 심화 검증 범위다.

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

초기 구현에서는 `source_version` 없이 Outbox 이벤트를 저장하고, 여러 worker가 `created_at, id` 기준으로 claim한 batch를 처리한다. 대상 ID별 RDB 현재 상태 재조회는 payload의 오래된 표시값을 그대로 쓰는 문제를 줄이지만, 조회 이후 동일 target에 대한 다른 worker의 쓰기와 interleaving되는 경우까지 보호하지 않는다.

다만 `source_version`이 없는 동안에도 재시도 중인 이전 이벤트가 최신 snapshot을 덮어쓰면 안 된다. 따라서 댓글 내용, 기사 제목/요약/게시일, 관심사 키워드, count 집계값처럼 나중 이벤트로 변경될 수 있는 snapshot 필드는 event payload의 표시값을 그대로 최종값으로 쓰지 않는다.

```text
E1 처리 실패
-> E2 처리 성공
-> E1 재시도
```

위 순서가 한 worker에서 순차 처리되면 E1 payload의 오래된 표시값을 덮어쓰지 않고, 처리 시점의 RDB 현재값을 다시 조회해 snapshot에 `$set`한다. 여러 worker가 같은 aggregate를 동시에 처리하면 앞서 설명한 stale write 경쟁이 남는다. 같은 aggregate의 후속 이벤트 보류나 `source_version` guard는 원본 엔티티 snapshot 필드 보호가 필요하고 엔티티 version 도입이 확정될 때 선택할 수 있는 대안으로 둔다.

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
