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
-> outbox_events 테이블에 이벤트 저장
-> RDB 커밋
-> 사용자 response 반환
-> outbox worker가 이벤트 처리
-> 필요한 경우 RDB에서 현재 snapshot 또는 집계값 조회
-> activity_histories upsert 또는 숨김 처리
-> *_activity_snapshots 저장 또는 갱신
```

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

이 추가 작업이 실제 response time에 얼마나 영향을 주는지는 수치로 단정하지 않고, 동일 조건의 성능 테스트로 검증한다.

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

커서에는 `occurredAt`과 `_id`를 함께 포함한다. snapshot 누락 또는 `visible=false` 항목을 제외한 뒤에는 limit을 채우기 위한 추가 조회를 하지 않는다. 따라서 응답 개수는 요청 limit보다 적을 수 있다.

## 후속 설계 결론

활동내역은 먼저 RDB로 구현한다.

MongoDB는 사전에 특정 기능에 고정해서 적용하지 않고, RDB 최적화 및 성능 검증 이후에도 병목이 남는 조회가 있을 때만 후속 적용 후보로 둔다.

이 문서의 저장 모델과 이벤트 처리 설명은 4개 활동내역 조회 기능 전체의 후보 설계이며, 후속 구현은 선정된 병목 기능부터 시작한다.

MongoDB에는 RDB 전체 데이터가 아니라 DTO 생성을 위한 최소 조회 모델만 저장한다.

RDB는 Source of Truth로 유지하고, MongoDB만 활동내역 조회 최적화를 위해 역정규화한다.

MongoDB Read Model 반영은 비동기로 처리하되, 이벤트 유실을 막기 위해 RDB 원본 변경과 outbox 이벤트 저장은 같은 트랜잭션에서 수행한다.

Outbox payload는 JSON 계열 타입으로 저장한다. 운영 DB가 PostgreSQL이면 `JSONB`, MySQL이면 `JSON`을 우선하고, 테스트 DB나 호환성 제약이 있으면 `TEXT` fallback을 허용한다.

Outbox 적용으로 쓰기 API response가 얼마나 증가하는지는 추정하지 않고, Outbox 적용 전/후 성능 테스트 결과를 기준으로 판단한다.

MongoDB 반영은 response 반환 이후 worker가 비동기로 수행하므로, 쓰기 API response 측정 범위와 분리한다.

카운트 집계값은 MongoDB 반영만을 위해 RDB counter를 바로 만들지 않고, 기본적으로 worker가 RDB에서 현재 집계값을 다시 조회해 MongoDB snapshot에 반영한다.

같은 polling batch 안에서 count 집계 이벤트를 병합하면 `event_type + snapshot 대상 ID` 기준으로 그룹화한다. MongoDB snapshot 대상 ID 기준 upsert와 현재 집계값 `$set`이 성공한 뒤에만 선택된 그룹 row 전체를 `PROCESSED`로 변경한다. 반영 실패 시에는 선택된 row 각각의 `retry_count`를 1 증가시키고, 증가 후 `max_retry_count`에 도달한 row는 `DEAD_LETTER`, 아직 한도 미만인 row는 `FAILED`로 전환한다. 상태 저장 전 중단되면 기존 상태가 남아 그룹 전체가 재시도된다. 재시도는 대상 ID 기준 upsert와 `$set`으로 멱등하게 처리한다.

사용자가 같은 대상에 대해 같은 종류의 활동을 반복하면 activity를 계속 추가하지 않고 `userId + type + targetType + targetId` 기준으로 기존 activity를 upsert한다.

이 upsert 기준은 후속 구현 시 unique index와 atomic upsert로 보장한다. 같은 outbox 이벤트가 재처리되거나 동일 활동 이벤트가 중복 발행되어도 activity는 중복 생성하지 않는다.

다만 natural key와 atomic upsert는 중복 문서 방지 계약이지 이벤트 순서 보호 계약은 아니다. activity의 `visible`, `status`, `occurredAt` 같은 상태 전이는 직렬화된 outbox `event_sequence`와 activity의 `lastAppliedEventSequence`를 비교하는 조건부 update로 보호한다.

worker는 activity update 시 `lastAppliedEventSequence`가 없거나 현재 이벤트의 `event_sequence`보다 작은 경우에만 상태 필드를 갱신한다. 더 오래된 이벤트가 재처리되면 MongoDB update는 no-op 처리하고 outbox row는 처리 완료로 볼 수 있다. `occurredAt`은 `$max` 또는 동등한 단조성 조건으로 갱신해 과거 이벤트가 최신 활동 시각을 낮추지 못하게 한다.

이 guard가 올바르게 동작하려면 같은 activity key를 변경할 수 있는 이벤트의 `event_sequence`가 RDB commit 순서와 어긋나지 않아야 한다. 후속 구현은 04-outbox-design의 projection key 기준 직렬화 계약을 따른다.

댓글 내용, 기사 제목/요약/게시일, 관심사 키워드, count 집계값처럼 나중 이벤트로 바뀔 수 있는 snapshot 필드는 오래된 payload로 덮어쓰지 않고, worker 처리 시점의 RDB 현재값을 조회해 반영한다.

`source_version`은 원본 엔티티 snapshot 필드의 순서 보호 후보로 남긴다. activity 상태 전이 보호는 여러 aggregate 이벤트가 같은 activity key를 갱신할 수 있으므로 `source_version`이 아니라 `event_sequence` 기준을 기본으로 둔다.

댓글 작성 또는 댓글 좋아요처럼 기사에 종속된 activity는 `parentTargetType=ARTICLE`, `parentTargetId=articleId`를 함께 저장한다. 기사 삭제 또는 비공개 처리 시 이 부모 식별자로 해당 기사에 속한 댓글 activity를 숨김 처리한다.

좋아요 취소, 구독 해제, 사용자/기사/댓글 논리삭제, 관심사 비노출처럼 기존 활동내역에서 더 이상 노출되면 안 되는 이벤트가 발생하면 기존 activity를 삭제하지 않고 `visible=false`로 변경한다.

논리삭제 이벤트는 기존에 `visible=true`인 activity만 숨김 처리한다. 이미 취소, 구독 해제, 다른 삭제 사유로 숨겨진 activity의 `status`는 덮어쓰지 않는다.

`status`는 activity가 노출되는지와 별개로 현재 상태 또는 숨김 사유를 표현한다. 기본 상태는 `ACTIVE`이며, 좋아요 취소는 `CANCELED`, 구독 해제는 `UNSUBSCRIBED`, 기사/댓글 삭제, 기사 비공개, 관심사 비노출 처리는 `TARGET_DELETED`, 사용자 삭제 또는 탈퇴는 `USER_DELETED`로 둔다.

`TARGET_DELETED`로 숨긴 activity에는 `hiddenByTargetType`, `hiddenByTargetId`를 함께 저장해 어떤 대상의 삭제 또는 비노출 전파로 visible=true activity가 숨겨졌는지 기록한다. 이미 숨겨진 activity는 다른 삭제 사유로 이 값이 갱신되지 않을 수 있으므로, 대상 복구 이벤트는 `hiddenByTargetType`, `hiddenByTargetId` 일치만으로 복구 후보를 제한하지 않는다.

대상 복구 시에는 activity만 `ACTIVE`로 되돌리지 않는다. 복구 대상과 관련될 수 있는 `status=TARGET_DELETED` activity 후보를 `targetType`, `targetId`, `parentTargetType`, `parentTargetId`로 찾고, 각 activity의 대상과 필요한 부모 대상이 RDB 기준으로 현재 노출 가능한 상태인지 다시 계산한다. 남은 차단 원인이 없으면 대상 snapshot을 RDB 현재 값으로 갱신해 `visible=true`로 복구한 뒤 activity를 `visible=true`, `status=ACTIVE`로 복구한다. `CANCELED`, `UNSUBSCRIBED`, `USER_DELETED` 상태는 대상 복구 이벤트로 자동 복구하지 않는다.

사용자, 기사, 댓글이 RDB에서 최종 물리삭제되면 MongoDB Read Model에서도 관련 `activity_histories`와 snapshot 문서를 제거한다. 물리삭제 이후에는 복구를 고려하지 않고, 복구 가능성은 논리삭제 상태에서만 유지한다.

물리삭제 cleanup 후에는 제거된 MongoDB 문서의 `lastAppliedEventSequence`도 사라질 수 있으므로, 삭제 전 지연 이벤트 재처리 차단을 sequence guard에만 의존하지 않는다. worker는 activity 또는 snapshot upsert 전에 RDB source row 존재 여부를 확인하고, source row가 없으면 payload만으로 문서를 재생성하지 않는다. cleanup 후 도착한 stale event는 no-op으로 처리하고 outbox row는 `PROCESSED`로 전환할 수 있다.

최종 구조는 다음과 같다.

```text
RDB 원본 데이터
-> 활동 이벤트 발생
-> 직렬화된 event_sequence가 포함된 outbox_events 저장
-> outbox worker가 event_sequence guard로 activity_histories 갱신
-> snapshot은 대상 ID 기준으로 갱신
-> 활동내역 API 조회
-> activity_histories 조회
-> 대상 snapshot 조회 및 매핑
-> DTO 변환
-> 클라이언트 응답
```

이 방식은 MongoDB를 단순 학습용으로 끼워 넣는 것이 아니라, RDB 조회 병목을 측정하고 근거를 기반으로 MongoDB Read Model을 적용하는 방향이다.
