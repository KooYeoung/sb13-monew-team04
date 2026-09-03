# MongoDB 저장 모델

[상위 문서](./README.md) | [이전: 개요 및 적용 대상 선정](./01-overview-and-selection.md) | [다음: 이벤트 핸들러 대상](./03-event-handler-targets.md)

## MongoDB 저장 방식

MongoDB에는 RDB 전체 데이터를 저장하지 않는다.

선정된 조회 기능에서 DTO를 만들기 위한 최소 Read Model만 저장한다.

이 문서는 4개 활동내역 조회 기능 전체를 후보 설계로 설명한다. 후속 적용이 확정되면 RDB 성능 검증 후 선정된 병목 기능부터 진행한다.

MongoDB에는 크게 두 종류의 문서를 저장한다.

```text
activity_histories
= 누가, 언제, 어떤 활동을, 어떤 대상에 했는지 저장하는 사용자 행동 이력

*_activity_snapshots
= 활동 대상이 화면에 표시될 때 필요한 최소 표시 정보
```

중요한 기준은 snapshot을 사용자별로 복사하지 않는 것이다.

예를 들어 댓글 `C1`을 100명이 좋아요했다면 MongoDB에는 다음과 같이 저장한다.

```text
activity_histories = 100개
comment_activity_snapshots = 1개
```

댓글 내용이 수정되면 `comment_activity_snapshots` 1개만 갱신한다. 100명의 활동 이력 문서를 모두 수정하지 않는다.

### activity_histories

`activity_histories`는 사용자 활동내역에 남을 행동이 발생했을 때 저장한다.

대상 행동은 다음과 같다.

```text
- 관심사 구독
- 댓글 작성
- 댓글 좋아요
- 뉴스 기사 조회
```

예시:

```json
{
  "_id": "sha256(activity|userId|type|targetType|targetId)",
  "sourceActivityId": "rdb-relation-uuid",
  "userId": "user-uuid",
  "type": "ARTICLE_VIEWED",
  "targetType": "ARTICLE",
  "targetId": "target-uuid",
  "parentTargetType": null,
  "parentTargetId": null,
  "occurredAt": "2026-08-15T10:30:00",
  "visible": true,
  "status": "ACTIVE",
  "createdAt": "2026-08-15T10:30:00",
  "updatedAt": "2026-08-15T10:30:00",
  "projectionVersion": 42,
  "tombstone": false
}
```

필드 의미는 다음과 같다.

```text
sourceActivityId
-> 기존 RDB 활동내역 응답의 id 계약을 유지하기 위한 구독, 댓글, 좋아요 또는 조회 row ID
-> MongoDB 문서의 `_id`와 별도로 저장하고 현재 관계 row가 바뀌면 갱신한다.

userId
-> 활동을 한 사용자

type
-> 활동 종류
-> 예: INTEREST_SUBSCRIBED, COMMENT_WRITTEN, COMMENT_LIKED, ARTICLE_VIEWED

targetType
-> 활동 대상 종류
-> 예: INTEREST, COMMENT, ARTICLE

targetId
-> 활동 대상 ID

parentTargetType
-> 활동 대상이 다른 도메인에 종속될 때 부모 대상 종류
-> 댓글 activity의 경우 ARTICLE
-> 부모 대상이 없으면 null 또는 필드 미저장

parentTargetId
-> 활동 대상이 다른 도메인에 종속될 때 부모 대상 ID
-> 댓글 activity의 경우 articleId
-> 부모 대상이 없으면 null 또는 필드 미저장

occurredAt
-> 활동이 발생한 시각
-> 최신순 정렬과 커서 페이지네이션 1차 기준

visible
-> 활동내역 조회 노출 여부
-> 기본값 true
-> 조회 API는 visible=true인 활동만 기본 조회한다.

status
-> 활동 상태 또는 visible=false가 된 이유
-> 기본값 ACTIVE
-> 예: ACTIVE, CANCELED, UNSUBSCRIBED, TARGET_DELETED, USER_DELETED

hiddenByTargetType
-> status=TARGET_DELETED일 때 visible=true였던 activity를 숨김 처리한 직접 대상 종류
-> 예: COMMENT, ARTICLE, INTEREST
-> 복구 판단을 위한 전체 원인 집합이 아니라 디버깅 및 보조 후보 조회용 정보
-> ACTIVE, CANCELED, UNSUBSCRIBED, USER_DELETED 상태에서는 null 또는 필드 미저장

hiddenByTargetId
-> status=TARGET_DELETED일 때 visible=true였던 activity를 숨김 처리한 직접 대상 ID
-> ACTIVE, CANCELED, UNSUBSCRIBED, USER_DELETED 상태에서는 null 또는 필드 미저장

projectionVersion
-> 원본 변경 transaction의 commit 순서를 나타내는 전역 단조 증가 버전
-> 저장 버전이 없거나 incoming version보다 작을 때만 문서를 갱신하는 CAS 조건

tombstone
-> 일반 문서와 논리삭제 hidden guard는 false
-> 물리삭제된 논리 키를 차단하는 scrubbed tombstone은 true
```

한 activity가 이미 `visible=false`이면 다른 논리삭제 또는 비노출 이벤트가 `hiddenByTargetType`, `hiddenByTargetId`를 덮어쓰지 않을 수 있다. 따라서 이 한 쌍만으로 복구 가능 여부를 판단하지 않고, 복구 시 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 후보를 찾은 뒤 RDB 현재 상태를 다시 계산한다.

MID4-135에서는 아래 컬렉션 이름과 인덱스 정의만 후속 구현 계약으로 코드에 반영했다. MID4-138에서 document와 `MongoTemplate` 기반 projection writer를 구현했다. `monew.mongodb.enabled=true`이고 인덱스 초기화가 활성화된 경우에만 애플리케이션 시작 시 인덱스를 멱등하게 생성한다.

Spring Data MongoDB의 repository 방식도 사용할 수 있지만 MID4-138 쓰기 경로는 `MongoTemplate`을 선택했다. 선행 조회 없이 결정적 `_id`와 `projectionVersion` 조건에 `$setOnInsert`, `$set`, `$max`, `$unset`을 조합한 원자적 CAS upsert가 필요하기 때문이다. 이 쓰기 호출은 reactive/non-blocking API가 아니며 worker thread가 각 MongoDB 명령의 완료를 기다린다.

```text
MongoRepository save 방식
-> 기존 문서 조회
-> 애플리케이션에서 insert/update 판단
-> 저장 사이에 경쟁 조건이 생길 수 있음

MongoTemplate 방식
-> 결정적 _id + 저장 projectionVersion < incoming version 조건의 단일 atomic upsert
-> 최초 생성 필드는 $setOnInsert
-> 변경 필드는 $set, occurredAt은 $max
-> 취소/논리삭제는 문서가 없어도 versioned hidden guard 생성
-> 물리삭제는 식별·표시 필드를 $unset한 scrubbed tombstone 생성
```

RDB UUID는 MongoDB 문서에서 canonical 문자열로 저장한다. MongoDB `_id`는 activity의 `userId|type|targetType|targetId`, snapshot의 `종류|대상 UUID`를 canonical key로 만들어 SHA-256으로 계산한다. 따라서 tombstone이 원본 식별 필드를 지워도 같은 논리 키의 과거 쓰기는 동일 `_id`에서 차단된다. 기존 활동내역 API의 id는 `sourceActivityId`에서 복원한다.

`activity_histories`의 필수 및 권장 인덱스는 다음과 같다.

```js
{ userId: 1, type: 1, targetType: 1, targetId: 1 } // unique partial(tombstone=false), ux_activity_histories_natural_key
{ userId: 1, type: 1, visible: 1, occurredAt: -1, _id: -1 } // idx_activity_histories_user_type_visible_cursor
{ userId: 1, visible: 1 } // idx_activity_histories_user_visible
{ targetType: 1, targetId: 1 } // idx_activity_histories_target
{ targetType: 1, parentTargetType: 1, parentTargetId: 1 } // idx_activity_histories_parent_target
{ hiddenByTargetType: 1, hiddenByTargetId: 1, status: 1 } // idx_activity_histories_hidden_by
```

MongoDB 인덱스에서 숫자는 저장값이 아니라 인덱스 정렬 방향을 의미한다.

```text
1 = 오름차순
-1 = 내림차순
```

각 인덱스의 목적은 다음과 같다.

```js
{ userId: 1, type: 1, targetType: 1, targetId: 1 }
```

동일 활동 조회 및 upsert에 사용한다.

```text
예: U1 + COMMENT_LIKED + COMMENT + C1
```

MID4-135의 인덱스 초기화가 이 조합을 `tombstone=false`인 문서에만 적용되는 partial unique index로 생성한다. MID4-138 worker는 같은 outbox 이벤트를 재처리하거나 동일 활동 이벤트가 중복 발행되어도 결정적 `_id`와 이 natural key를 기준으로 하나의 activity만 유지한다. 이는 새 조회용 인덱스를 추가한 것이 아니라 기존 고유 인덱스가 scrubbed tombstone을 제외하도록 조건을 조정한 것이다.

이 인덱스와 versioned atomic upsert는 중복 문서와 stale overwrite를 함께 막는다. event payload의 과거 표시값이 아니라 worker가 조회한 RDB 현재 상태를 반영하고, 같은 문서에는 더 큰 `projectionVersion`만 저장한다.

```js
{ userId: 1, type: 1, visible: 1, occurredAt: -1, _id: -1 }
```

사용자별 활동내역 조회에 사용한다.

```text
userId = U1
type = COMMENT_WRITTEN
visible = true
order by occurredAt desc, _id desc
```

활동내역은 최신순 조회가 기본이므로 `occurredAt`은 내림차순인 `-1`을 사용한다. 같은 `occurredAt`에 여러 activity가 있을 수 있으므로 `_id`를 보조 정렬 기준으로 함께 사용한다.

```js
{ userId: 1, visible: 1 }
```

사용자 논리삭제 또는 사용자 물리삭제 시 해당 사용자의 activity를 찾는 데 사용한다.

```js
{ targetType: 1, targetId: 1 }
```

특정 기사, 댓글, 관심사가 삭제되거나 비공개 처리되었을 때 관련 activity를 찾아 숨김 처리하는 데 사용한다.

```js
{ targetType: 1, parentTargetType: 1, parentTargetId: 1 }
```

부모 대상 삭제 또는 비공개 처리 시 자식 activity를 찾아 숨김 처리하는 데 사용한다.

```text
targetType = COMMENT
parentTargetType = ARTICLE
parentTargetId = A1
```

예를 들어 기사 A1이 삭제되면 해당 기사에 속한 댓글 작성 activity와 댓글 좋아요 activity를 이 인덱스로 찾아 숨김 처리한다.

```js
{ hiddenByTargetType: 1, hiddenByTargetId: 1, status: 1 }
```

직접 숨김 원인을 기준으로 상태를 확인하거나 단순 후보를 좁힐 때 사용한다. 복구 최종 판단은 이 인덱스만으로 하지 않고, activity의 대상 및 부모 식별자로 RDB 현재 상태를 다시 계산해 결정한다.

```text
hiddenByTargetType = ARTICLE
hiddenByTargetId = A1
status = TARGET_DELETED
```

### activity 생성 및 수정 기준

사용자가 행동할 때마다 무조건 새 activity를 만들지는 않는다.

기준은 다음 조합이다.

```text
userId + type + targetType + targetId
```

이 조합이 없으면 새로 생성하고, 이미 있으면 기존 activity를 수정한다. MongoDB 쓰기는 find 후 insert/update를 나누지 않고 이 natural key 기준의 atomic upsert로 처리한다.

activity 상태 변경은 이벤트 종류만 보고 payload의 과거 상태를 그대로 적용하지 않는다. worker는 polling batch에서 같은 대상 ID를 참조하는 이벤트를 묶고, 좋아요·구독의 활성 여부, 원본과 부모 대상의 존재 및 노출 여부를 RDB에서 batch 조회한다. 그 결과로 `visible`, `status`, `hiddenByTargetType`, `hiddenByTargetId`를 계산해 natural key 기준으로 atomic upsert한다.

따라서 중복 이벤트, 실패 후 재시도, RDB transaction commit 순서와 worker 처리 순서의 역전이 발생해도 각 처리는 당시의 RDB 현재 상태를 반영한다. 두 transaction 사이에 worker가 실행되어 일시적인 중간 상태가 반영되더라도 나중에 commit된 transaction의 Outbox 이벤트가 다시 현재 상태를 조회하므로 최종 MongoDB Read Model은 RDB에 수렴한다.

한 worker의 claim batch 내부에서는 이벤트를 순차 처리하지만, 여러 worker instance는 서로 다른 batch의 같은 target을 동시에 처리할 수 있다. 이때 claim UUID는 이벤트 row 소유권만 보호하고 projection 순서는 전역 `projectionVersion`과 MongoDB CAS가 보호한다.

producer는 원본 변경 transaction 안에서 singleton `outbox_projection_clock` row를 `PESSIMISTIC_WRITE`로 잠그고 다음 버전을 발급한다. 잠금은 commit 또는 rollback까지 유지되므로 단순 DB sequence와 달리 버전 순서가 commit 순서와 일치한다. MongoDB query는 `_id`가 같고 저장 `projectionVersion`이 없거나 incoming보다 작은 경우에만 upsert한다. 조건에서 탈락한 upsert가 `_id` 중복으로 실패하면 같은 `_id`의 저장 버전이 incoming 이상인지 재확인한 경우에만 stale 성공으로 처리한다.

```text
W1: target=C1, projectionVersion=41 이벤트 claim -> RDB 상태 조회
W2: target=C1, projectionVersion=42 이벤트 claim -> RDB 상태 조회
W2: MongoDB에 V42 반영
W1: 저장 version < 41 조건 불일치 -> stale 성공(no-op)

결과
-> content, visible, status, snapshot 표시값이 V41로 회귀하지 않음
-> occurredAt은 live write 안에서 기존대로 $max 적용
```

이 설계는 target별 worker 직렬화 없이 다중 instance의 동일 target 쓰기를 허용한다. 대신 모든 Outbox producer가 요청 중 하나의 clock row를 잠그므로 쓰기 transaction 사이에 전역 대기가 생길 수 있다. 현재는 성능 측정 없이 polling 인덱스를 추가하지 않으며, 이 잠금 비용은 MongoDB Read Model 운영 전 별도로 관찰한다.

worker는 `actor_user_id` 집합으로 사용자 존재 여부와 `deleted_at`도 batch 조회한다. actor가 논리삭제 또는 물리삭제된 활동 이벤트는 새 activity를 활성화하지 않는다. 삭제 전에 payload로 수집한 활동 natural key마다 versioned hidden guard 또는 tombstone을 만들므로, 문서가 없던 경우에도 사용자 삭제 후 stale write가 차단된다.

`occurredAt`은 최신 활동 정렬 기준이므로 역행하지 않게 처리한다. 좋아요, 구독, 기사 조회처럼 활동 시각을 갱신하는 이벤트는 `$max` 또는 동등한 단조성 조건으로만 `occurredAt`을 갱신한다. 취소, 삭제와 비노출은 기존 `occurredAt`을 변경하지 않으며, 후속 복구 이벤트도 과거 시각으로 낮추지 않아야 한다.

예시는 다음과 같다.

```text
U1 + COMMENT_LIKED + COMMENT + C1
U1 + ARTICLE_VIEWED + ARTICLE + A1
U1 + INTEREST_SUBSCRIBED + INTEREST + I1
```

처음 발생한 활동은 새로 생성한다.

```text
댓글 C1 작성
-> COMMENT_WRITTEN + COMMENT + C1 생성
-> parentTargetType=ARTICLE, parentTargetId=A1 저장

댓글 C2 작성
-> COMMENT_WRITTEN + COMMENT + C2 생성
-> parentTargetType=ARTICLE, parentTargetId=A1 저장

댓글 C1 좋아요
-> COMMENT_LIKED + COMMENT + C1 생성
-> parentTargetType=ARTICLE, parentTargetId=A1 저장

관심사 I1 구독
-> INTEREST_SUBSCRIBED + INTEREST + I1 생성

기사 A1 조회
-> ARTICLE_VIEWED + ARTICLE + A1 생성
```

이미 같은 activity가 있으면 새로 만들지 않고 기존 문서를 갱신한다.

아래 복구·재노출 흐름은 후속 설계다. MID4-138 이벤트 목록과 projection handler에는 별도 복구 이벤트 및 기존 `TARGET_DELETED` activity의 재활성화 처리가 포함되지 않는다. 후속 구현에서는 activity만 `ACTIVE`로 바꾸지 않고, 먼저 RDB 기준 대상과 필요한 부모 대상이 현재 노출 가능한 상태인지 확인한 뒤 snapshot과 activity를 함께 복구해야 한다. 대상이 아직 RDB에서 삭제 또는 비노출 상태이면 activity를 재활성화하지 않고 `hiddenByTargetType`, `hiddenByTargetId`는 복구 성공 시에만 제거한다.

```text
댓글 C1 좋아요 취소
-> 기존 COMMENT_LIKED + COMMENT + C1
-> visible=false
-> status=CANCELED

댓글 C1 다시 좋아요
-> 기존 COMMENT_LIKED + COMMENT + C1
-> RDB 댓글 C1과 부모 기사 A1이 모두 노출 가능한 상태인지 확인
-> comment_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 보장
-> visible=true
-> status=ACTIVE
-> hiddenByTargetType, hiddenByTargetId 제거
-> occurredAt은 기존 값과 이벤트 occurredAt 중 큰 값으로 갱신
-> 좋아요 활성 여부는 RDB 현재 상태 기준으로 결정

관심사 I1 구독 취소
-> 기존 INTEREST_SUBSCRIBED + INTEREST + I1
-> visible=false
-> status=UNSUBSCRIBED

관심사 I1 다시 구독
-> 기존 INTEREST_SUBSCRIBED + INTEREST + I1
-> RDB 관심사 I1이 노출 가능한 상태인지 확인
-> interest_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 보장
-> visible=true
-> status=ACTIVE
-> hiddenByTargetType, hiddenByTargetId 제거
-> occurredAt은 기존 값과 이벤트 occurredAt 중 큰 값으로 갱신
-> 구독 활성 여부는 RDB 현재 상태 기준으로 결정

기사 A1 다시 조회
-> 기존 ARTICLE_VIEWED + ARTICLE + A1
-> RDB 기사 A1이 노출 가능한 상태인지 확인
-> article_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 보장
-> visible=true
-> status=ACTIVE
-> hiddenByTargetType, hiddenByTargetId 제거
-> occurredAt은 기존 값과 이벤트 occurredAt 중 큰 값으로 갱신
-> 기사 존재 및 노출 여부는 RDB 현재 상태 기준으로 결정
```

댓글 수정처럼 활동 자체가 다시 발생한 것이 아니라 대상 표시 정보만 바뀐 경우에는 activity를 새로 만들지 않는다.

```text
댓글 C1 수정
-> activity_histories 변경 없음
-> comment_activity_snapshots 갱신

기사 A1 제목/요약 수정
-> activity_histories 변경 없음
-> article_activity_snapshots 갱신

관심사 I1 키워드 수정
-> activity_histories 변경 없음
-> interest_activity_snapshots 갱신
```

논리삭제와 비노출 처리는 기존 activity를 숨긴다.

사용자, 기사, 댓글 논리삭제 이벤트는 기존에 `visible=true`인 activity만 상태 변경 대상으로 본다. 이미 좋아요 취소, 구독 해제, 다른 삭제 사유로 숨겨진 activity의 `status`는 덮어쓰지 않는다. 따라서 `hiddenByTargetType`, `hiddenByTargetId`는 activity를 숨길 수 있는 모든 원인의 집합이 아니다.

```text
사용자 U1 논리삭제
-> userId=U1, visible=true인 모든 activity visible=false, status=USER_DELETED

댓글 C1 논리삭제
-> COMMENT_WRITTEN + COMMENT + C1 중 visible=true인 activity만 visible=false, status=TARGET_DELETED
-> hiddenByTargetType=COMMENT, hiddenByTargetId=C1 저장
-> COMMENT_LIKED + COMMENT + C1 중 visible=true인 activity만 visible=false, status=TARGET_DELETED
-> hiddenByTargetType=COMMENT, hiddenByTargetId=C1 저장
-> comment_activity_snapshots visible=false

기사 A1 논리삭제
-> ARTICLE_VIEWED + ARTICLE + A1 중 visible=true인 activity만 visible=false, status=TARGET_DELETED
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=A1 저장
-> parentTargetType=ARTICLE, parentTargetId=A1인 댓글 활동 중 visible=true인 activity만 visible=false, status=TARGET_DELETED
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=A1 저장
-> article_activity_snapshots visible=false

관심사 I1 비노출
-> INTEREST_SUBSCRIBED + INTEREST + I1 중 visible=true인 activity만 visible=false, status=TARGET_DELETED
-> hiddenByTargetType=INTEREST, hiddenByTargetId=I1 저장
-> interest_activity_snapshots visible=false
```

대상 복구 이벤트는 `hiddenByTargetType`, `hiddenByTargetId` 일치만으로 판단하지 않는다. 복구 대상과 관련될 수 있는 `status=TARGET_DELETED` activity 후보를 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 찾고, 각 activity의 대상과 부모 대상이 RDB 기준으로 모두 노출 가능한지 다시 계산한다. 남은 차단 원인이 없을 때만 snapshot과 activity를 함께 복구한다.

```text
댓글 C1 복구
-> RDB 댓글 C1과 부모 기사 A1이 모두 노출 가능한 상태인지 확인
-> comment_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 처리
-> targetType=COMMENT, targetId=C1, status=TARGET_DELETED인 activity 후보를 확인
-> 남은 차단 원인이 없는 activity만 visible=true, status=ACTIVE 처리
-> 복구된 activity의 hiddenByTargetType, hiddenByTargetId 제거

기사 A1 복구
-> RDB 기사 A1이 노출 가능한 상태인지 확인
-> article_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 처리
-> targetType=ARTICLE, targetId=A1, status=TARGET_DELETED인 기사 activity 후보를 확인
-> parentTargetType=ARTICLE, parentTargetId=A1, status=TARGET_DELETED인 댓글 activity 후보를 확인
-> 댓글 activity 후보는 각 댓글이 노출 가능한 경우 comment_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 처리
-> 각 activity의 대상과 부모 대상에 남은 차단 원인이 없는 경우에만 visible=true, status=ACTIVE 처리
-> 복구된 activity의 hiddenByTargetType, hiddenByTargetId 제거

관심사 I1 재노출
-> RDB 관심사 I1이 노출 가능한 상태인지 확인
-> interest_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 처리
-> targetType=INTEREST, targetId=I1, status=TARGET_DELETED인 activity 후보를 확인
-> 남은 차단 원인이 없는 activity만 visible=true, status=ACTIVE 처리
-> 복구된 activity의 hiddenByTargetType, hiddenByTargetId 제거
```

핵심 규칙은 다음과 같다.

```text
동일 대상에 대한 동일 활동은 upsert
새 대상에 대한 활동은 insert
댓글 activity는 부모 기사 식별자 저장
취소/논리삭제/비노출은 기존 activity 숨김
TARGET_DELETED는 hiddenByTargetType, hiddenByTargetId로 직접 숨김 원인을 보조 저장
복구는 RDB 대상/부모 상태 재계산 후 snapshot visible=true 복구와 activity ACTIVE 복구를 함께 처리
activity 상태 전이는 대상 ID별 RDB 현재 상태 batch 재조회 결과로 수렴
occurredAt은 $max 또는 동등한 단조 조건으로 갱신
물리삭제는 MongoDB Read Model의 식별·표시 필드를 제거한 scrubbed tombstone으로 차단
수정은 activity가 아니라 snapshot 갱신
```

### activity snapshots

snapshot 컬렉션은 활동 대상이 화면에 표시될 때 필요한 최소 정보를 저장한다.

댓글 snapshot 예시는 다음과 같다.

```json
{
  "_id": "sha256(comment|comment-uuid)",
  "commentId": "comment-uuid",
  "articleId": "article-uuid",
  "articleTitle": "뉴스 제목",
  "authorUserId": "comment-author-uuid",
  "authorNickname": "작성자",
  "content": "댓글 내용",
  "likeCount": 3,
  "visible": true,
  "createdAt": "2026-08-15T10:30:00",
  "updatedAt": "2026-08-15T10:30:00",
  "projectionVersion": 42,
  "tombstone": false
}
```

뉴스 기사 snapshot 예시는 다음과 같다.

```json
{
  "_id": "sha256(article|article-uuid)",
  "articleId": "article-uuid",
  "title": "뉴스 제목",
  "summary": "뉴스 요약",
  "source": "NAVER",
  "sourceUrl": "https://example.com/article",
  "publishedAt": "2026-08-15T09:00:00",
  "viewCount": 10,
  "commentCount": 2,
  "visible": true,
  "updatedAt": "2026-08-15T10:30:00",
  "projectionVersion": 42,
  "tombstone": false
}
```

관심사 snapshot 예시는 다음과 같다.

```json
{
  "_id": "sha256(interest|interest-uuid)",
  "interestId": "interest-uuid",
  "name": "AI",
  "keywords": ["인공지능", "머신러닝"],
  "subscriberCount": 15,
  "visible": true,
  "updatedAt": "2026-08-15T10:30:00",
  "projectionVersion": 42,
  "tombstone": false
}
```

snapshot 컬렉션은 사용자 활동마다 새로 복사하지 않고 대상 ID 기준으로 하나만 유지한다.

```text
comment_activity_snapshots
-> commentId 기준 1개

article_activity_snapshots
-> articleId 기준 1개

interest_activity_snapshots
-> interestId 기준 1개
```

MID4-135에서 준비한 snapshot 인덱스는 다음과 같다. MID4-138 worker는 대상 ID 기준 atomic upsert로 snapshot을 저장하고 갱신한다.

```js
{ commentId: 1 } // unique partial(tombstone=false), ux_comment_activity_snapshots_comment_id
{ articleId: 1, visible: 1 } // idx_comment_activity_snapshots_article_visible
{ articleId: 1 } // unique partial(tombstone=false), ux_article_activity_snapshots_article_id
{ interestId: 1 } // unique partial(tombstone=false), ux_interest_activity_snapshots_interest_id
```

### 조회 흐름

activity 조회는 기본적으로 `occurredAt DESC, _id DESC` 순서로 정렬한다. 커서 페이지네이션도 `occurredAt`과 `_id`를 함께 사용한다.

snapshot 조회 후 snapshot이 없거나 `visible=false`이면 해당 activity는 응답에서 제외한다. 초기 정책에서는 제외된 항목만큼 추가 activity를 더 조회해 `limit`을 반드시 채우지 않는다. 따라서 Read Model 반영 지연이나 삭제 전파 상황에서는 응답 개수가 요청 `limit`보다 적을 수 있다.

최근 작성 댓글 조회는 다음과 같이 처리한다.

```text
activity_histories에서 userId + type=COMMENT_WRITTEN 조회
-> targetId(commentId) 목록 추출
-> comment_activity_snapshots 조회
-> DTO 변환
```

최근 좋아요한 댓글 조회는 다음과 같이 처리한다.

```text
activity_histories에서 userId + type=COMMENT_LIKED 조회
-> targetId(commentId) 목록 추출
-> comment_activity_snapshots 조회
-> DTO 변환
```

최근 본 뉴스 조회는 다음과 같이 처리한다.

```text
activity_histories에서 userId + type=ARTICLE_VIEWED 조회
-> targetId(articleId) 목록 추출
-> article_activity_snapshots 조회
-> DTO 변환
```

구독 중인 관심사 조회는 다음과 같이 처리한다.

```text
activity_histories에서 userId + type=INTEREST_SUBSCRIBED 조회
-> targetId(interestId) 목록 추출
-> interest_activity_snapshots 조회
-> DTO 변환
```

### 제외한 방식

사용자 문서 하나에 관심사, 뉴스, 댓글 활동 리스트를 모두 넣는 방식은 기본안에서 제외한다.

```text
제외 이유
- 사용자 활동이 많아질수록 문서가 계속 커진다.
- 배열 일부 수정, 삭제, 페이지네이션이 복잡해진다.
- 뉴스/댓글/관심사 변경 시 사용자별 문서를 많이 갱신해야 한다.
```

사용자 문서에는 ID 리스트만 저장하고 뉴스, 댓글, 관심사를 다시 조회하는 방식도 기본안에서 제외한다.

```text
제외 이유
- 사용자 문서의 ID 배열이 계속 커진다.
- 활동 발생 시각, 숨김 상태, 취소 상태를 ID 배열과 별도로 관리해야 한다.
- 결국 activity_histories와 유사한 구조가 필요해진다.
```

중요한 점은 MongoDB 문서가 API DTO에 직접 종속되지 않도록 하는 것이다.

```text
MongoDB 조회 모델 -> DTO 변환 -> API 응답
```

MongoDB 조회 모델과 DTO는 비슷할 수 있지만 책임이 다르다.

```text
MongoDB 조회 모델 = 조회 최적화를 위한 데이터 모델
DTO = API 응답을 위한 표현 모델
```

DTO 변경이 곧바로 MongoDB 스키마 변경을 강제하지 않도록, MongoDB 문서는 해당 활동의 의미를 중심으로 설계한다.

## 상태 변경 처리

논리삭제, 비공개, 좋아요 취소 같은 상태 변경은 MongoDB 문서를 물리 삭제하기보다 상태 필드로 처리한다.

예시:

```json
{
  "visible": false,
  "status": "TARGET_DELETED",
  "hiddenByTargetType": "ARTICLE",
  "hiddenByTargetId": "article-uuid",
  "updatedAt": "2026-08-15T12:00:00"
}
```

RDB는 원본 상태의 기준이고, MongoDB는 조회 최적화용 사본이다.

따라서 원본 데이터에 상태 변경이 발생하면 해당 변경을 MongoDB Read Model에도 반영할 정책이 필요하다.

상태 값은 다음 기준으로 사용한다.

```text
기본 활동
-> visible=true
-> status=ACTIVE

좋아요 취소
-> visible=false
-> status=CANCELED

구독 취소
-> visible=false
-> status=UNSUBSCRIBED

활동 대상 삭제, 비공개 또는 비노출
-> visible=false
-> status=TARGET_DELETED
-> hiddenByTargetType, hiddenByTargetId에 직접 숨김 원인 저장

사용자 삭제 또는 탈퇴
-> visible=false
-> status=USER_DELETED
```

논리삭제 이벤트는 기존에 `visible=true`인 activity만 변경한다. 이미 `visible=false`인 activity는 기존 `status`를 유지한다. 따라서 `hiddenByTargetType`, `hiddenByTargetId`는 복구 가능 여부를 단독으로 결정하는 전체 차단 원인 목록이 아니다.

구체적인 예시는 다음과 같다.

```text
COMMENT_LIKED + 좋아요 취소
-> visible=false
-> status=CANCELED

INTEREST_SUBSCRIBED + 구독 취소
-> visible=false
-> status=UNSUBSCRIBED

COMMENT_WRITTEN + 댓글 삭제
-> visible=false
-> status=TARGET_DELETED
-> hiddenByTargetType=COMMENT, hiddenByTargetId=commentId

ARTICLE_VIEWED + 기사 삭제 또는 비공개
-> visible=false
-> status=TARGET_DELETED
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId

INTEREST_SUBSCRIBED + 관심사 비노출
-> visible=false
-> status=TARGET_DELETED
-> hiddenByTargetType=INTEREST, hiddenByTargetId=interestId

사용자 U1 삭제 또는 탈퇴
-> userId=U1, visible=true인 activity만 visible=false
-> status=USER_DELETED
```

후속 복구 이벤트를 구현할 때는 `hiddenByTargetType`, `hiddenByTargetId` 일치만으로 복구 후보를 제한하지 않는다. 복구 대상과 관련될 수 있는 `status=TARGET_DELETED` activity를 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 찾고, RDB 기준 대상과 필요한 부모 대상에 남은 삭제, 비공개, 비노출 차단 원인이 없는 경우에만 복구한다. `CANCELED`, `UNSUBSCRIBED`, `USER_DELETED` 상태는 대상 복구 이벤트로 자동 복구하지 않는다.

## 물리삭제 처리

물리삭제는 RDB에서 복구 대상이 아니게 최종 제거되는 단계다. MongoDB에서는 문서를 실제 remove하지 않고 결정적 `_id`, `projectionVersion`, `tombstone=true`, `visible=false`, `updatedAt`만 남기는 scrubbed tombstone으로 바꾼다. 사용자 ID, target ID, 제목, 내용 같은 식별·표시 필드는 `$unset`해 조회 모델에서는 제거된 것과 같게 취급하면서, 같은 논리 키의 과거 이벤트는 버전 CAS로 차단한다.

```text
사용자 U1 물리삭제
-> 삭제 전에 수집한 U1 관련 activity natural key마다 scrubbed tombstone upsert
-> U1이 작성한 댓글 snapshot도 scrubbed tombstone upsert

댓글 C1 물리삭제
-> commentId=C1의 결정적 snapshot _id에 scrubbed tombstone upsert
-> 댓글 작성/좋아요 activity key마다 scrubbed tombstone upsert

기사 A1 물리삭제
-> articleId=A1 및 자식 comment snapshot의 결정적 _id에 scrubbed tombstone upsert
-> 조회, 댓글 작성, 댓글 좋아요 activity key마다 scrubbed tombstone upsert
```

물리삭제 이후에는 복구를 고려하지 않는다. 복구 가능성은 논리삭제 상태에서만 유지한다.

producer는 댓글·기사·관심사·사용자 삭제 전에 영향받는 activity natural key와 comment snapshot ID를 RDB에서 수집해 payload에 넣는다. worker는 source row 존재 여부를 재확인하고 각 키에 삭제 이벤트의 버전으로 tombstone을 물질화한다. 물리삭제 이후 더 낮은 버전의 지연 이벤트가 도착하면 동일 `_id`의 더 높은 tombstone 버전 때문에 no-op이 된다. 같은 버전 재시도도 이미 반영된 문서는 건너뛰면서 아직 빠진 fan-out 문서는 이어서 만들 수 있다.

현재 Read Model은 기본 비활성화이고 운영 조회 경로에 연결되지 않았으므로 기존 무버전 문서의 온라인 변환은 하지 않는다. 이 계약을 적용한 로컬 환경은 MongoDB 볼륨/컬렉션을 비우고 초기 projection을 다시 수행해야 한다.

추천 인덱스 예시는 다음과 같다.

```js
{ userId: 1, type: 1, visible: 1, occurredAt: -1, _id: -1 }
{ userId: 1, visible: 1 }
{ targetType: 1, targetId: 1 }
{ targetType: 1, parentTargetType: 1, parentTargetId: 1 }
{ hiddenByTargetType: 1, hiddenByTargetId: 1, status: 1 }
```

첫 번째 인덱스는 사용자별 최신 활동 조회와 커서 페이지네이션에 사용한다. 두 번째 인덱스는 사용자 논리삭제 또는 사용자 물리삭제 시 해당 사용자의 activity를 찾는 데 사용한다. 세 번째 인덱스는 특정 대상의 삭제 또는 상태 변경 반영에 사용한다. 네 번째 인덱스는 기사 삭제 또는 비공개 처리 시 해당 기사에 속한 댓글 activity를 숨김 또는 tombstone 처리하는 데 사용한다. 다섯 번째 인덱스는 직접 숨김 원인을 기준으로 상태를 확인하거나 단순 후보를 좁힐 때 사용한다. 복구 최종 판단은 대상 및 부모 식별자와 RDB 현재 상태 재계산으로 수행한다.
