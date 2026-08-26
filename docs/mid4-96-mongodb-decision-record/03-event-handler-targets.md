# 이벤트 핸들러 대상

[상위 문서](./README.md) | [이전: MongoDB 저장 모델](./02-mongodb-storage-model.md) | [다음: Outbox 설계](./04-outbox-design.md)

MongoDB Read Model을 적용하면 RDB 원본 데이터의 변경을 MongoDB 조회 모델에 반영하는 이벤트 핸들러가 필요하다.

이벤트 핸들러는 MongoDB를 원본처럼 다루기 위한 것이 아니라, RDB 원본 변경을 조회 최적화용 모델에 반영하기 위한 projection 역할을 한다.

아래 이벤트 핸들러 대상은 4개 활동내역 조회 기능 전체에 대한 후보 설계다. 후속 구현은 성능 검증 후 MongoDB 적용 후보로 결정된 기능에 필요한 이벤트부터 시작한다.

## 공통 삭제 이벤트

논리삭제 개념은 사용자, 기사, 댓글에 둔다. 관심사는 노출 상태 변경을 같은 대상 숨김 이벤트로 처리한다.

논리삭제 이벤트는 기존에 `visible=true`인 activity만 숨김 처리한다. 이미 `CANCELED`, `UNSUBSCRIBED`, `TARGET_DELETED`, `USER_DELETED`로 숨겨진 activity의 `status`는 덮어쓰지 않는다.

`TARGET_DELETED`로 숨길 때는 어떤 대상의 삭제 또는 비노출 전파로 visible=true activity가 숨겨졌는지 `hiddenByTargetType`, `hiddenByTargetId`를 함께 저장한다. 이미 숨겨진 activity는 다른 삭제 사유로 `hiddenByTargetType`, `hiddenByTargetId`가 갱신되지 않을 수 있으므로, 대상 복구 이벤트는 이 한 쌍만으로 복구 후보를 제한하지 않는다.

아래 이벤트별 `visible`, `status`, `occurredAt` 갱신은 Outbox 설계의 `event_sequence` guard를 통과한 경우에만 반영한다. 오래된 이벤트 재처리가 최신 activity 상태를 덮어쓰면 안 된다.

```text
사용자 논리삭제 또는 탈퇴
-> userId=deletedUserId, visible=true인 activity visible=false, status=USER_DELETED 처리

사용자 물리삭제
-> userId=deletedUserId인 activity_histories 문서 제거

댓글 논리삭제
-> targetType=COMMENT, targetId=commentId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=COMMENT, hiddenByTargetId=commentId 저장
-> comment snapshot visible=false 처리

댓글 물리삭제
-> comment snapshot 제거
-> targetType=COMMENT, targetId=commentId인 activity_histories 문서 제거

기사 논리삭제
-> targetType=ARTICLE, targetId=articleId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장
-> targetType=COMMENT, parentTargetType=ARTICLE, parentTargetId=articleId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장
-> article snapshot visible=false 처리

기사 물리삭제
-> article snapshot 제거
-> targetType=ARTICLE, targetId=articleId인 activity_histories 문서 제거
-> targetType=COMMENT, parentTargetType=ARTICLE, parentTargetId=articleId인 activity_histories 문서 제거
```

물리삭제 이후에는 복구를 고려하지 않는다. 삭제 전 `PENDING` 또는 재시도 가능한 `FAILED` 이벤트가 나중에 처리되더라도, worker는 activity 또는 snapshot upsert 전에 RDB 현재 상태를 확인한다. source row가 없으면 payload만으로 MongoDB 문서를 재생성하지 않고 no-op 처리한다.

## 공통 복구 이벤트

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
-> 해당 commentId를 참조하는 작성 댓글 activity 제거

기사 논리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 visible=true 작성 댓글 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장

기사 물리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 작성 댓글 activity 제거

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
-> 해당 commentId를 참조하는 좋아요 댓글 activity 제거

기사 논리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 visible=true 좋아요 댓글 활동 visible=false, status=TARGET_DELETED 처리
-> hiddenByTargetType=ARTICLE, hiddenByTargetId=articleId 저장

기사 물리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 좋아요 댓글 activity 제거

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
-> 해당 articleId를 참조하는 최근 본 뉴스 activity 제거

조회수 변경
-> ARTICLE_VIEW_COUNT_CHANGED 이벤트로 처리
-> worker가 RDB 현재 viewCount를 재조회해 article snapshot의 viewCount를 $set

댓글 작성 또는 삭제
-> ARTICLE_COMMENT_COUNT_CHANGED 이벤트로 처리
-> worker가 RDB 현재 commentCount를 재집계해 article snapshot의 commentCount를 $set
```
