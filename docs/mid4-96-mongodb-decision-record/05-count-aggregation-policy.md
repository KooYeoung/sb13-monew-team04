# 카운트 집계 처리 기준

[상위 문서](./README.md) | [이전: Outbox 설계](./04-outbox-design.md) | [다음: 후속 적용 검토 흐름 및 결론](./06-final-flow-and-conclusion.md)

MongoDB Read Model은 조회 성능을 위해 역정규화될 수 있지만, RDB 원본 모델까지 바로 역정규화하지는 않는다.

예를 들어 댓글 좋아요 수를 RDB에서 `COUNT(*)`로 집계하는 구조라면, MongoDB 반영만을 위해 댓글 테이블에 `like_count` 컬럼을 바로 추가하지 않는다.

```text
RDB = 정규화된 원본 데이터 유지
MongoDB = 활동내역 조회를 위한 역정규화 Read Model
```

댓글 좋아요 수, 기사 조회수, 기사 댓글 수, 관심사 구독자 수 같은 집계값은 원본 엔티티의 `source_version`과 억지로 연결하지 않는다.

예를 들어 댓글 내용 수정은 댓글 엔티티의 version으로 순서를 판단할 수 있지만, 댓글 좋아요 수는 좋아요 테이블의 집계 결과이므로 댓글 version만으로 순서를 판단하기 어렵다.

activity 상태 전이는 outbox `event_sequence`와 activity의 `lastAppliedEventSequence` 비교로 보호하지만, 이 기준을 count snapshot 전체에 전역 적용하지는 않는다. count snapshot은 이벤트 payload의 과거 count를 신뢰하지 않고 RDB 현재 집계값을 다시 조회해 수렴시키는 정책을 기본으로 둔다.

따라서 집계값 변경 이벤트는 다음 기준으로 처리한다.

집계 이벤트의 핵심은 이벤트 payload에 들어온 과거 값을 그대로 반영하는 것이 아니라, worker가 처리하는 시점의 RDB 현재값으로 MongoDB snapshot을 수렴시키는 것이다. 따라서 중복 이벤트나 일부 순서 역전이 발생해도 마지막으로 처리된 집계 이벤트는 처리 시점의 현재 집계값을 다시 반영한다.

```text
COMMENT_LIKE_CHANGED
-> payload에는 commentId, eventId, occurredAt 같은 최소 식별 정보만 담는다.
-> worker가 이벤트 처리 시점에 RDB에서 현재 likeCount를 다시 집계한다.
-> MongoDB comment snapshot에 likeCount를 $set으로 반영한다.

ARTICLE_COMMENT_COUNT_CHANGED
-> worker가 RDB에서 현재 commentCount를 다시 집계한다.
-> MongoDB article snapshot에 commentCount를 $set으로 반영한다.

ARTICLE_VIEW_COUNT_CHANGED
-> payload에는 articleId, eventId, occurredAt 같은 최소 식별 정보만 담는다.
-> worker가 이벤트 처리 시점에 RDB에서 현재 viewCount를 다시 조회한다.
-> MongoDB article snapshot에 viewCount를 $set으로 반영한다.

INTEREST_SUBSCRIBER_COUNT_CHANGED
-> worker가 RDB에서 현재 subscriberCount를 다시 집계한다.
-> MongoDB interest snapshot에 subscriberCount를 $set으로 반영한다.
```

기사 조회는 발생 빈도가 높을 수 있으므로 조회수 변경 이벤트는 worker가 outbox row마다 RDB를 1회씩 재조회하지 않는다.

초기 기준은 다음과 같다.

```text
ARTICLE_VIEW_COUNT_CHANGED
-> producer는 articleId 기준 조회수 변경 신호를 저장한다.
-> worker는 한 번의 polling batch 안에서 같은 event_type + articleId 이벤트를 하나로 합친다.
-> 같은 batch에서 articleId별 RDB 현재 viewCount 재조회는 최대 1회만 수행한다.
-> MongoDB article snapshot에는 누적 증감값이 아니라 RDB 현재 viewCount를 $set한다.
```

병합 처리된 outbox row의 상태 전이는 그룹 전체를 기준으로 한다.

```text
count 이벤트 병합 그룹
-> 기준: 같은 polling batch에서 선택된 row 중 event_type + snapshot 대상 ID가 같은 row
-> 대상 ID 예: commentId, articleId, interestId

처리 성공
-> snapshot 대상 RDB source row 존재 확인 성공
-> RDB 현재 count 조회 성공
-> MongoDB snapshot 대상 ID 기준 upsert 및 현재 count $set 성공
-> 선택된 그룹 row 전체를 PROCESSED, 동일 processed_at으로 변경

cleanup 이후 stale event
-> snapshot 대상 RDB source row가 물리삭제되어 없음
-> MongoDB snapshot upsert를 수행하지 않음
-> 선택된 그룹 row 전체를 stale event로 보고 PROCESSED 처리

처리 실패
-> RDB 현재 count 조회 또는 MongoDB snapshot $set 실패
-> 선택된 그룹 row 각각의 retry_count를 1 증가
-> 증가 후 retry_count >= max_retry_count인 row는 DEAD_LETTER로 변경
-> 아직 한도 미만인 row는 FAILED로 변경하고 row별 retry_count 기준으로 next_retry_at 설정
-> last_error는 실패한 그룹 row 전체에 같은 원인을 기록

worker 중단
-> MongoDB 반영 성공 후 outbox 상태 저장 전에 중단되면 그룹 row가 다시 선택될 수 있음
-> 재처리 시 snapshot 대상 ID 기준 upsert와 현재 count $set으로 멱등 처리
-> MongoDB 반영 전 또는 실패 상태 저장 전 중단되면 기존 상태가 남아 그룹 전체가 다시 재시도됨
```

병합 그룹의 대표 row만 `PROCESSED`로 바꾸지 않는다. 선택된 그룹 row 전체가 완료 처리되어야 같은 batch에서 이미 반영한 신호가 반복 선택되지 않는다. 또한 MongoDB 반영 전에 그룹 row를 먼저 완료 처리하지 않는다. 반영 실패 시 count 변경 신호가 유실될 수 있기 때문이다.

물리삭제 cleanup 이후 삭제 전 count 이벤트가 재처리될 수 있으므로, count snapshot upsert도 activity와 동일하게 RDB source row 존재 확인을 먼저 수행한다. source row가 없으면 payload만으로 snapshot을 재생성하지 않고, 선택된 병합 그룹 row 전체를 no-op 및 `PROCESSED`로 정리한다.

성공 상태 전이는 그룹 전체에 동일하게 적용하지만, 실패 시 retry 이력은 row별로 보존한다. 같은 그룹 안에 `retry_count=4`인 row와 `retry_count=1`인 row가 있고 `max_retry_count=5`에서 다시 실패하면, 첫 번째 row는 `DEAD_LETTER`로 전환하고 두 번째 row는 `retry_count=2`, `FAILED`, 2회차 기준 `next_retry_at`으로 전환한다.

조회수 이벤트가 worker 처리량을 지속적으로 초과하면 기사 조회 이벤트와 count 갱신 이벤트를 분리하거나, 일정 주기 batch/coalescing publisher로 전환한다. 이 경우 viewCount snapshot은 실시간 정확값이 아니라 짧은 지연을 허용하는 표시값으로 취급한다.

이 방식은 worker가 추가 RDB 조회를 수행한다는 단점이 있지만, RDB 원본 모델을 불필요하게 역정규화하지 않고 이벤트 순서가 일부 바뀌어도 최신 집계값으로 수렴하기 쉽다.

카운트 처리 대안은 다음과 같이 남긴다.

```text
1. RDB counter 컬럼 또는 counter 테이블 추가

장점
- 카운트 조회와 버전 처리가 명확하다.
- 이벤트 payload에 확정된 카운트와 counter version을 담기 쉽다.

단점
- RDB 원본 모델이 역정규화된다.
- 카운트 정합성 관리 로직이 추가된다.

판단
- 성능 병목이 검증되기 전에는 기본안으로 두지 않는다.
```

```text
2. worker가 RDB에서 현재 집계값 재조회

장점
- RDB 정규화 구조를 유지한다.
- 중복 이벤트나 순서 역전이 있어도 현재 집계값으로 수렴하기 쉽다.

단점
- worker가 추가 RDB 조회를 수행한다.

판단
- 후속 MongoDB Read Model 적용 시 기본안 후보로 둔다.
```

```text
3. 이벤트 payload에 카운트 최종값 포함

장점
- worker가 RDB를 다시 조회하지 않아도 된다.

단점
- 카운트 버전 기준이 명확하지 않으면 오래된 이벤트가 최신 카운트를 덮어쓸 수 있다.

판단
- 별도 counter version이 없는 집계값에는 기본 적용하지 않는다.
```

정리하면 `source_version`은 댓글 내용, 기사 제목, 관심사 키워드처럼 원본 엔티티 자체가 변경되는 필드의 순서 판단에 사용할 수 있다.

다만 현재는 엔티티 version 필드 추가가 먼저 필요하므로 별도 검토 대상으로 둔다.

`likeCount`처럼 다른 테이블을 기준으로 집계하는 값에는 엔티티 `source_version`을 억지로 붙이지 않는다.

`event_sequence`는 activity 상태 전이를 오래된 이벤트 재처리로부터 보호하기 위한 기준이다. 집계값처럼 RDB 현재값을 재조회해 `$set`하는 snapshot 필드의 순서 판단을 `event_sequence` 하나로 대체하지 않는다.
