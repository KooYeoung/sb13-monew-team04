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
  "userId": "user-uuid",
  "type": "ARTICLE_VIEWED",
  "targetType": "ARTICLE",
  "targetId": "target-uuid",
  "parentTargetType": null,
  "parentTargetId": null,
  "occurredAt": "2026-08-15T10:30:00",
  "visible": true,
  "status": "ACTIVE",
  "lastAppliedEventSequence": 1024,
  "createdAt": "2026-08-15T10:30:00",
  "updatedAt": "2026-08-15T10:30:00"
}
```

필드 의미는 다음과 같다.

```text
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

lastAppliedEventSequence
-> 이 activity에 마지막으로 반영된 outbox event_sequence
-> 오래된 이벤트 재처리가 최신 visible, status, occurredAt를 덮어쓰지 않도록 조건부 update에 사용
-> 새 activity 생성 시 현재 이벤트의 event_sequence 저장

hiddenByTargetType
-> status=TARGET_DELETED일 때 visible=true였던 activity를 숨김 처리한 직접 대상 종류
-> 예: COMMENT, ARTICLE, INTEREST
-> 복구 판단을 위한 전체 원인 집합이 아니라 디버깅 및 보조 후보 조회용 정보
-> ACTIVE, CANCELED, UNSUBSCRIBED, USER_DELETED 상태에서는 null 또는 필드 미저장

hiddenByTargetId
-> status=TARGET_DELETED일 때 visible=true였던 activity를 숨김 처리한 직접 대상 ID
-> ACTIVE, CANCELED, UNSUBSCRIBED, USER_DELETED 상태에서는 null 또는 필드 미저장
```

한 activity가 이미 `visible=false`이면 다른 논리삭제 또는 비노출 이벤트가 `hiddenByTargetType`, `hiddenByTargetId`를 덮어쓰지 않을 수 있다. 따라서 이 한 쌍만으로 복구 가능 여부를 판단하지 않고, 복구 시 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 후보를 찾은 뒤 RDB 현재 상태를 다시 계산한다.

필수 및 권장 인덱스는 다음과 같다.

```js
{ userId: 1, type: 1, targetType: 1, targetId: 1 } // unique
{ userId: 1, type: 1, visible: 1, occurredAt: -1, _id: -1 }
{ userId: 1, visible: 1 }
{ targetType: 1, targetId: 1 }
{ targetType: 1, parentTargetType: 1, parentTargetId: 1 }
{ hiddenByTargetType: 1, hiddenByTargetId: 1, status: 1 }
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

후속 구현 시 필수 unique index로 만든다. worker가 같은 outbox 이벤트를 재처리하거나 동일 활동 이벤트가 중복 발행되어도 이 natural key를 기준으로 하나의 activity만 유지한다.

이 인덱스와 atomic upsert는 중복 문서 생성을 막기 위한 장치다. 이벤트 처리 순서 역전까지 보장하지는 않으므로, activity 상태 전이는 `lastAppliedEventSequence` 조건으로 별도 보호한다.

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

activity 상태 변경은 natural key만으로 update하지 않는다. worker는 현재 outbox 이벤트의 `event_sequence`가 기존 activity의 `lastAppliedEventSequence`보다 큰 경우에만 `visible`, `status`, `hiddenByTargetType`, `hiddenByTargetId` 같은 상태 필드를 갱신한다. 기존 값이 없거나 더 작은 경우에는 갱신하고, 기존 값이 같거나 더 크면 오래된 재처리 이벤트로 보고 no-op 처리한다.

`occurredAt`은 최신 활동 정렬 기준이므로 역행하지 않게 처리한다. 좋아요, 구독, 기사 조회처럼 활동 시각을 갱신하는 이벤트는 `$max` 또는 동등한 단조성 조건으로만 `occurredAt`을 갱신한다. 취소, 삭제, 비노출, 복구 이벤트도 과거 이벤트가 최신 `occurredAt`을 낮추지 못하게 한다.

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

기존 activity를 다시 노출하거나 대상 복구 이벤트를 처리할 때는 activity만 `ACTIVE`로 바꾸지 않는다. 먼저 RDB 기준 대상과 필요한 부모 대상이 현재 노출 가능한 상태인지 확인하고, 대상 snapshot을 RDB 현재 값으로 갱신해 `visible=true`를 보장한 뒤 activity를 복구한다. 대상이 아직 RDB에서 삭제 또는 비노출 상태이면 activity를 재활성화하지 않는다. `hiddenByTargetType`, `hiddenByTargetId`는 복구 성공 시에만 제거한다.

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
-> lastAppliedEventSequence 갱신

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
-> lastAppliedEventSequence 갱신

기사 A1 다시 조회
-> 기존 ARTICLE_VIEWED + ARTICLE + A1
-> RDB 기사 A1이 노출 가능한 상태인지 확인
-> article_activity_snapshots를 RDB 현재 값으로 갱신하고 visible=true 보장
-> visible=true
-> status=ACTIVE
-> hiddenByTargetType, hiddenByTargetId 제거
-> occurredAt은 기존 값과 이벤트 occurredAt 중 큰 값으로 갱신
-> lastAppliedEventSequence 갱신
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
activity 상태 전이는 lastAppliedEventSequence 조건으로 오래된 이벤트 재처리 방지
occurredAt은 $max 또는 동등한 단조 조건으로 갱신
물리삭제는 MongoDB Read Model에서도 제거
수정은 activity가 아니라 snapshot 갱신
```

### activity snapshots

snapshot 컬렉션은 활동 대상이 화면에 표시될 때 필요한 최소 정보를 저장한다.

댓글 snapshot 예시는 다음과 같다.

```json
{
  "commentId": "comment-uuid",
  "articleId": "article-uuid",
  "content": "댓글 내용",
  "likeCount": 3,
  "visible": true,
  "createdAt": "2026-08-15T10:30:00",
  "updatedAt": "2026-08-15T10:30:00"
}
```

뉴스 기사 snapshot 예시는 다음과 같다.

```json
{
  "articleId": "article-uuid",
  "title": "뉴스 제목",
  "summary": "뉴스 요약",
  "source": "NAVER",
  "publishedAt": "2026-08-15T09:00:00",
  "viewCount": 10,
  "commentCount": 2,
  "visible": true,
  "updatedAt": "2026-08-15T10:30:00"
}
```

관심사 snapshot 예시는 다음과 같다.

```json
{
  "interestId": "interest-uuid",
  "name": "AI",
  "keywords": ["인공지능", "머신러닝"],
  "subscriberCount": 15,
  "visible": true,
  "updatedAt": "2026-08-15T10:30:00"
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
  "deletedAt": "2026-08-15T12:00:00"
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

대상 복구 이벤트는 `hiddenByTargetType`, `hiddenByTargetId` 일치만으로 복구 후보를 제한하지 않는다. 복구 대상과 관련될 수 있는 `status=TARGET_DELETED` activity를 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 찾고, RDB 기준 대상과 필요한 부모 대상에 남은 삭제, 비공개, 비노출 차단 원인이 없는 경우에만 복구한다. `CANCELED`, `UNSUBSCRIBED`, `USER_DELETED` 상태는 대상 복구 이벤트로 자동 복구하지 않는다.

## 물리삭제 처리

물리삭제는 RDB에서 복구 대상이 아니게 최종 제거되는 단계다. 이 경우 MongoDB는 조회 최적화용 사본이므로 관련 Read Model 문서를 제거한다.

```text
사용자 U1 물리삭제
-> activity_histories에서 userId=U1인 문서 제거

댓글 C1 물리삭제
-> comment_activity_snapshots에서 commentId=C1인 문서 제거
-> activity_histories에서 targetType=COMMENT, targetId=C1인 문서 제거

기사 A1 물리삭제
-> article_activity_snapshots에서 articleId=A1인 문서 제거
-> activity_histories에서 targetType=ARTICLE, targetId=A1인 문서 제거
-> activity_histories에서 targetType=COMMENT, parentTargetType=ARTICLE, parentTargetId=A1인 문서 제거
```

물리삭제 이후에는 복구를 고려하지 않는다. 복구 가능성은 논리삭제 상태에서만 유지한다.

물리삭제 cleanup으로 activity 또는 snapshot 문서가 제거되면 `lastAppliedEventSequence`도 함께 사라질 수 있다. 따라서 물리삭제 이후 지연 이벤트나 재처리 이벤트가 도착했을 때의 재생성 차단 기준은 sequence guard가 아니라 RDB source row 존재 여부다. worker는 upsert 전에 원본 사용자, 기사, 댓글, 관심사 등 필요한 source row가 존재하고 노출 가능한지 확인하며, source row가 없으면 MongoDB 문서를 다시 만들지 않고 no-op 처리한다.

추천 인덱스 예시는 다음과 같다.

```js
{ userId: 1, type: 1, visible: 1, occurredAt: -1, _id: -1 }
{ userId: 1, visible: 1 }
{ targetType: 1, targetId: 1 }
{ targetType: 1, parentTargetType: 1, parentTargetId: 1 }
{ hiddenByTargetType: 1, hiddenByTargetId: 1, status: 1 }
```

첫 번째 인덱스는 사용자별 최신 활동 조회와 커서 페이지네이션에 사용한다. 두 번째 인덱스는 사용자 논리삭제 또는 사용자 물리삭제 시 해당 사용자의 activity를 찾는 데 사용한다. 세 번째 인덱스는 특정 대상의 삭제 또는 상태 변경 반영에 사용한다. 네 번째 인덱스는 기사 삭제 또는 비공개 처리 시 해당 기사에 속한 댓글 activity를 숨김 처리하거나 제거하는 데 사용한다. 다섯 번째 인덱스는 직접 숨김 원인을 기준으로 상태를 확인하거나 단순 후보를 좁힐 때 사용한다. 복구 최종 판단은 대상 및 부모 식별자와 RDB 현재 상태 재계산으로 수행한다.
