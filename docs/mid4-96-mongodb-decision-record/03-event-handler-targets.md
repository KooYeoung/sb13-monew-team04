# 이벤트 핸들러 대상

[상위 문서](./README.md) | [이전: MongoDB 저장 모델](./02-mongodb-storage-model.md) | [다음: Outbox 설계](./04-outbox-design.md)

MongoDB Read Model을 적용하면 RDB 원본 데이터의 변경을 MongoDB 조회 모델에 반영하는 이벤트 핸들러가 필요하다.

이벤트 핸들러는 MongoDB를 원본처럼 다루기 위한 것이 아니라, RDB 원본 변경을 조회 최적화용 모델에 반영하기 위한 projection 역할을 한다.

아래 이벤트 핸들러 대상은 4개 활동내역 조회 기능 전체에 대한 후보 설계다. 후속 구현은 성능 검증 후 MongoDB 적용 후보로 결정된 기능에 필요한 이벤트부터 시작한다.

## 공통 삭제 이벤트

논리삭제 개념은 사용자, 기사, 댓글에 둔다. 관심사는 노출 상태 변경을 같은 대상 숨김 이벤트로 처리한다.

논리삭제 이벤트는 기존에 `visible=true`인 activity만 숨김 처리한다. 이미 `CANCELED`, `UNSUBSCRIBED`, `TARGET_DELETED`, `USER_DELETED`로 숨겨진 activity의 `status`는 덮어쓰지 않는다.

`TARGET_DELETED`로 숨길 때는 어떤 대상의 삭제 또는 비노출 전파로 visible=true activity가 숨겨졌는지 `hiddenByTargetType`, `hiddenByTargetId`를 함께 저장한다. 이미 숨겨진 activity는 다른 삭제 사유로 `hiddenByTargetType`, `hiddenByTargetId`가 갱신되지 않을 수 있으므로, 대상 복구 이벤트는 이 한 쌍만으로 복구 후보를 제한하지 않는다.

아래 이벤트는 MongoDB에 적용할 상태값 자체가 아니라 RDB 현재 상태를 다시 확인해야 한다는 신호다. worker는 polling batch에서 대상 ID별로 source row 존재 여부, 노출 여부, 좋아요·구독 활성 여부, actor 사용자의 존재·논리삭제 상태와 필요한 count를 묶어 조회한 뒤 `visible`, `status`, `occurredAt`과 snapshot을 갱신한다. payload의 과거 mutable 값을 그대로 반영하지 않는다. 삭제된 actor의 지연 이벤트는 activity를 생성하거나 기존 `USER_DELETED` 상태를 활성화하지 않는다.

```text
사용자 논리삭제 또는 탈퇴
-> userId=deletedUserId, visible=true인 activity visible=false, status=USER_DELETED 처리

사용자 물리삭제
-> userId=deletedUserId인 기존 activity와 삭제 전 수집한 activity key를 scrubbed tombstone 처리
-> 작성 댓글 snapshot도 scrubbed tombstone 처리

댓글 논리삭제
-> targetType=COMMENT, targetId=commentId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=COMMENT, hiddenByTargetId=commentId 저장
-> comment snapshot visible=false 처리

댓글 물리삭제
-> comment snapshot을 scrubbed tombstone 처리
-> targetType=COMMENT, targetId=commentId인 activity key를 scrubbed tombstone 처리

기사 논리삭제
-> targetType=ARTICLE, targetId=articleId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장
-> targetType=COMMENT, parentTargetType=ARTICLE, parentTargetId=articleId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장
-> article snapshot visible=false 처리

기사 물리삭제
-> article과 자식 comment snapshot을 scrubbed tombstone 처리
-> 조회, 댓글 작성, 댓글 좋아요 activity key를 scrubbed tombstone 처리
```

물리삭제 이후에는 복구를 고려하지 않는다. 삭제 전 `PENDING` 또는 재시도 가능한 `FAILED` 이벤트가 나중에 처리되더라도, worker는 RDB 현재 상태와 결정적 `_id`의 더 높은 `projectionVersion` tombstone을 확인한다. stale 이벤트는 MongoDB 문서를 재생성하지 않고 no-op 처리한다. MID4-248은 댓글·기사·관심사·사용자 물리삭제의 기존 문서 bulk cleanup과 payload fan-out tombstone 물질화, 그리고 아래 두 stale replay를 실제 MongoDB 통합 테스트로 고정했다.

```text
PENDING 또는 FAILED 이벤트 version=41
-> 물리삭제 tombstone version=42가 먼저 반영됨
-> worker가 source row 부재를 확인하고 version=41 tombstone을 다시 시도
-> 저장 version < incoming version 조건이 거짓이므로 no-op
-> outbox row는 정상 처리된 stale 이벤트로 PROCESSED 전환 가능

worker가 version=41의 삭제 전 RDB 상태를 이미 읽음
-> 다른 worker가 물리삭제 tombstone version=42를 먼저 반영
-> 첫 worker가 오래된 live snapshot/activity upsert를 시도
-> 같은 결정적 _id의 version=42가 유지되고 표시 필드는 복원되지 않음
```

## Payload와 RDB 재조회 기준

Outbox의 공통 envelope는 row의 `id`, `event_type`, `aggregate_type`, `aggregate_id`, `actor_user_id`, `occurred_at` 컬럼에 저장한다. payload에는 공통 envelope를 중복하지 않고 이벤트 발생 시 이미 알고 있는 사실과 worker가 추가 대상을 찾는 데 필요한 식별자를 저장한다. 변경 가능한 표시값과 관계의 현재 활성 상태는 projection의 최종값으로 신뢰하지 않는다.

| 구분 | payload 사용 필드 | worker RDB batch 재조회 | source row 없음 |
| --- | --- | --- | --- |
| 공통 envelope 컬럼 | event ID, event type, aggregate type/ID, actor user ID, occurredAt | 원본 및 필요한 부모 대상의 존재·노출 여부 | payload만으로 생성하지 않고 숨김·삭제 또는 no-op |
| 관심사·기사·사용자 변경 | `action` | 현재 표시값과 노출 상태 | snapshot을 복원하지 않음 |
| 댓글·댓글 좋아요 | 부모 `articleId`, `action` | 댓글·기사의 현재 상태와 현재 좋아요 row 존재 여부 | activity를 생성하지 않고 숨김·삭제 또는 no-op |
| count 변경 | `action=COUNT_CHANGED` | `aggregate_id`가 가리키는 대상의 현재 likeCount, viewCount, commentCount, subscriberCount | snapshot을 복원하지 않음 |
| 삭제·닉네임 변경 | `action`, 삭제 전에 확보한 `impact.activityKeys`, `impact.commentSnapshotIds`; 사용자 물리삭제 count 영향 ID | 남아 있는 연관 데이터와 MongoDB cleanup/갱신 대상 확인 | 결정적 `_id` hidden guard/tombstone 또는 snapshot 갱신에 사용하되 표시값 복원에는 사용하지 않음 |

MID4-137 구현에서 `aggregate_id`와 `actor_user_id`는 payload에 중복하지 않는다. Java payload record의 `OutboxEventAction`은 JSON 문자열로 직렬화되며, 예를 들어 댓글 작성 payload는 `{"articleId":"...","action":"WRITTEN"}` 형태로 저장된다.

이미 알고 있는 불변 값은 payload snapshot으로 사용할 수 있다. 다만 producer가 이미 알고 있는 mutable 값도 감사·디버깅 목적으로 payload에 포함할 수 있을 뿐, worker는 이를 MongoDB의 최종 상태로 사용하지 않는다.

## 공통 복구 이벤트(후속 설계)

현재 RDB 도메인에는 논리삭제된 댓글·기사·관심사를 같은 ID로 복구하거나 재노출하는 동작이 없고, 이에 대응하는 Outbox event type과 producer도 없다. S3 기사 복원은 기존 기사를 같은 ID로 되살리는 흐름이 아니라 새 UUID의 기사를 생성하므로 여기서 말하는 재노출 이벤트가 아니다. 따라서 MID4-248은 아래 후보 설계를 구현하지 않고, 현재 존재하는 물리삭제와 stale replay 계약만 검증한다.

대상 복구 이벤트는 activity만 재활성화하지 않는다. 복구 대상과 관련될 수 있는 `status=TARGET_DELETED` activity 후보를 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 찾고, RDB 기준 대상과 필요한 부모 대상이 현재 노출 가능한 상태인지 다시 계산한다. 남은 차단 원인이 없으면 대상 snapshot을 RDB 현재 값으로 갱신해 `visible=true`로 복구한 뒤 activity를 `visible=true`, `status=ACTIVE`로 복구한다. 대상이 아직 RDB에서 삭제 또는 비노출 상태이면 activity 재활성화를 하지 않는다. `CANCELED`, `UNSUBSCRIBED`, `USER_DELETED` 상태는 대상 복구 이벤트로 자동 복구하지 않는다.

```text
댓글 복구
-> RDB 댓글과 부모 기사가 모두 노출 가능한 상태인지 확인
-> comment snapshot을 RDB 현재 값으로 갱신하고 visible=true 처리
-> targetType=COMMENT, targetId=commentId, status=TARGET_DELETED인 activity 후보를 확인
-> 남은 차단 원인이 없는 activity만 visible=true, status=ACTIVE 처리
-> 복구된 activity의 hiddenByTargetType, hiddenByTargetId 제거

기사 복구
-> RDB 기사가 노출 가능한 상태인지 확인
-> article snapshot을 RDB 현재 값으로 갱신하고 visible=true 처리
-> targetType=ARTICLE, targetId=articleId, status=TARGET_DELETED인 기사 activity 후보를 확인
-> parentTargetType=ARTICLE, parentTargetId=articleId, status=TARGET_DELETED인 댓글 activity 후보를 확인
-> 댓글 activity 후보는 각 댓글이 노출 가능한 경우 comment snapshot을 RDB 현재 값으로 갱신하고 visible=true 처리
-> 각 activity의 대상과 부모 대상에 남은 차단 원인이 없는 경우에만 visible=true, status=ACTIVE 처리
-> 복구된 activity의 hiddenByTargetType, hiddenByTargetId 제거

관심사 재노출
-> RDB 관심사가 노출 가능한 상태인지 확인
-> interest snapshot을 RDB 현재 값으로 갱신하고 visible=true 처리
-> targetType=INTEREST, targetId=interestId, status=TARGET_DELETED인 activity 후보를 확인
-> 남은 차단 원인이 없는 activity만 visible=true, status=ACTIVE 처리
-> 복구된 activity의 hiddenByTargetType, hiddenByTargetId 제거
```

### 구독 중인 관심사

```text
관심사 구독
-> RDB 관심사가 노출 가능한 상태인지 확인
-> interest snapshot을 RDB 현재 값으로 갱신하고 visible=true 보장
-> 사용자별 구독 관심사 활동 생성 또는 재활성화
-> visible=true, status=ACTIVE 처리
-> hiddenByTargetType, hiddenByTargetId 제거

구독 해제
-> 해당 사용자의 구독 관심사 활동 visible=false, status=UNSUBSCRIBED 처리

관심사 키워드 추가 또는 삭제
-> 관심사 snapshot의 keywords 갱신

관심사 비노출 또는 제거
-> 해당 interestId를 참조하는 visible=true 구독 관심사 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=INTEREST, hiddenByTargetId=interestId 저장
-> interest snapshot visible=false 처리

구독자 수 변경
-> INTEREST_SUBSCRIBER_COUNT_CHANGED 이벤트로 처리
-> worker가 RDB 현재 subscriberCount를 재집계해 interest snapshot의 subscriberCount를 $set
```

### 최근 작성 댓글

```text
댓글 작성
-> 작성 댓글 활동 생성

댓글 수정
-> 댓글 snapshot의 content, updatedAt 등 표시 데이터 갱신
-> 활동 정렬 기준은 댓글 작성 시각을 유지

댓글 논리삭제
-> 해당 commentId를 참조하는 visible=true 작성 댓글 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=COMMENT, hiddenByTargetId=commentId 저장

댓글 물리삭제
-> 해당 commentId를 참조하는 작성 댓글 activity scrubbed tombstone 처리

기사 논리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 visible=true 작성 댓글 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장

기사 물리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 작성 댓글 activity scrubbed tombstone 처리

댓글 좋아요 수 변경
-> 댓글 snapshot의 likeCount 갱신 필요 신호로 처리
```

### 최근 좋아요한 댓글

```text
댓글 좋아요
-> RDB 댓글과 부모 기사가 모두 노출 가능한 상태인지 확인
-> comment snapshot을 RDB 현재 값으로 갱신하고 visible=true 보장
-> 좋아요 댓글 활동 생성 또는 재활성화
-> visible=true, status=ACTIVE 처리
-> hiddenByTargetType, hiddenByTargetId 제거

좋아요 취소
-> 해당 사용자의 좋아요 댓글 활동 visible=false, status=CANCELED 처리

댓글 수정
-> 댓글 snapshot의 content, updatedAt 등 표시 데이터 갱신

댓글 논리삭제
-> 해당 commentId를 참조하는 visible=true 좋아요 댓글 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=COMMENT, hiddenByTargetId=commentId 저장

댓글 물리삭제
-> 해당 commentId를 참조하는 좋아요 댓글 activity scrubbed tombstone 처리

기사 논리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 visible=true 좋아요 댓글 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장

기사 물리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 좋아요 댓글 activity scrubbed tombstone 처리

댓글 좋아요 수 변경
-> 댓글 snapshot의 likeCount 갱신 필요 신호로 처리
```

### 최근 조회 기사

```text
기사 조회
-> RDB 기사가 노출 가능한 상태인지 확인
-> article snapshot을 RDB 현재 값으로 갱신하고 visible=true 보장
-> 최근 본 뉴스 활동 생성 또는 재활성화
-> visible=true, status=ACTIVE 처리
-> hiddenByTargetType, hiddenByTargetId 제거
-> 같은 사용자가 같은 기사를 다시 조회하면 occurredAt을 단조 조건으로 최신화

기사 수정
-> 기사 snapshot의 title, summary, source, publishedAt 등 표시 데이터 갱신

기사 논리삭제
-> 해당 articleId를 참조하는 visible=true 최근 본 뉴스 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장

기사 물리삭제
-> 해당 articleId를 참조하는 최근 본 뉴스 activity scrubbed tombstone 처리

조회수 변경
-> ARTICLE_VIEW_COUNT_CHANGED 이벤트로 처리
-> worker가 RDB 현재 viewCount를 재조회해 article snapshot의 viewCount를 $set

댓글 작성 또는 삭제
-> ARTICLE_COMMENT_COUNT_CHANGED 이벤트로 처리
-> worker가 RDB 현재 commentCount를 재집계해 article snapshot의 commentCount를 $set
```
