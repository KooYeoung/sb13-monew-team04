# 초기 데이터 투영 및 정합성 검증

[상위 문서](./README.md) | [이전: RDB 조회 성능 측정 시나리오](./08-rdb-performance-test-scenarios.md)

## 목적과 범위

MID4-249는 MongoDB 조회 경로 전환 전에 기존 RDB 활동 데이터를 Read Model로 채우고,
중단 후 재개 및 최종 정합성 확인이 가능한 실행 경로를 준비한다. 조회 API 전환은
MID4-139, shadow read 비교는 MID4-250 범위이며 이 작업에서는 수행하지 않는다.

초기 투영 대상은 현재 노출 가능한 다음 네 활동이다.

| stage | RDB 원본 | MongoDB activity type | snapshot |
| --- | --- | --- | --- |
| `SUBSCRIPTION` | 활성 구독 | `INTEREST_SUBSCRIBED` | 관심사 |
| `COMMENT_WRITTEN` | 활성 댓글 | `COMMENT_WRITTEN` | 댓글 |
| `COMMENT_LIKED` | 활성 댓글 좋아요 | `COMMENT_LIKED` | 댓글 |
| `ARTICLE_VIEWED` | 활성 기사 조회 | `ARTICLE_VIEWED` | 기사 |

논리삭제된 사용자, 삭제 또는 비노출 댓글, 삭제 기사와 연결된 row는 scan 단계에서
제외한다. scan과 source 조회 사이에 상태가 바뀌는 경계는 공통 projection handler가
RDB 현재 상태를 다시 판단해 활성 문서 생성을 막는다.

## 실행 단위와 checkpoint

`read_model_backfill_runs`는 실행 UUID마다 다음 진행 상태를 저장한다.

```text
run_id
stage / status
last_processed_id
pending_last_id
processed_count
claim_id / claimed_at / claim_until
retry_count / last_error
verification_report / verified_at
created_at / updated_at
```

stage와 status는 Java enum으로 검증하며 DB CHECK 제약은 추가하지 않는다. cursor는 각
원본 테이블의 UUID PK만 사용해 오름차순 keyset 방식으로 이동한다. 초기 투영 전용
인덱스는 성능 측정 없이 추가하지 않는다.

상태 전이는 다음과 같다.

```text
PENDING -> RUNNING -> PENDING
                   -> FAILED -> RUNNING

마지막 stage 완료 -> VERIFYING -> COMPLETED
                              -> VERIFICATION_FAILED -> 첫 stage 재투영
```

한 page를 claim할 때 `pending_last_id`를 먼저 저장하고, MongoDB page 전체가 성공한
뒤에만 이를 `last_processed_id`로 옮긴다. 중간에 실패하거나 인스턴스가 종료되면 완료
cursor는 그대로이고 같은 범위를 다시 처리한다.

검증 scan은 non-empty page의 `lastSourceRowId`가 마지막 이벤트 ID와 일치하고 이전
cursor와 다르며 이미 사용한 cursor가 아닌지 확인한다. 이 진행 불변식이 깨지면
`RMB_007` 커스텀 예외로 즉시 중단하므로 scanner 오류가 무한 반복으로 이어지지 않는다.
UUID 대소 비교는 DB 정렬 규칙과 Java 비교 규칙이 다를 수 있어 사용하지 않는다.
또한 매 page 조회 전과 MongoDB 검증 전에 claim lease 상태를 확인한다.

```text
last_processed_id = A, 다음 page 끝 = D
-> pending_last_id = D, claim 시작
-> A 다음의 B/C는 반영했지만 D에서 실패
-> last_processed_id는 A, pending_last_id는 D 유지
-> 다음 claim이 (A, D] 전체를 재실행
-> 결정적 _id + CAS로 B/C 중복 생성 없이 D까지 수렴
-> 성공 후 last_processed_id = D
```

완료된 동일 `run-id`는 다시 claim하지 않는다. 전체를 새로 검증·재투영해야 한다면 새
UUID를 사용한다.

## 다중 인스턴스와 실시간 Outbox의 순서 보호

여러 애플리케이션 인스턴스가 같은 run-id를 polling해도 checkpoint row의 비관적 잠금과
claim UUID/lease가 한 인스턴스에만 page를 배정한다. claim 만료 판정과 heartbeat 갱신은
인스턴스 시계가 아닌 DB `CURRENT_TIMESTAMP`를 사용한다. 소유권을 잃은 worker는 다음
MongoDB 명령이나 상태 저장을 시작하지 않는다.

초기 투영은 Outbox worker와 같은 `ProjectionCommand`, RDB source reader, projection
handler 및 MongoDB writer를 사용한다. 별도의 과거 Outbox row를 대량 생성하지 않는다.

claim transaction은 `outbox_projection_clock`을 잠가 전역 `projectionVersion`을 먼저
발급하고, 그 잠금을 유지한 상태에서 page와 RDB 현재 source를 읽은 뒤 commit한다.
MongoDB 반영은 이 transaction 밖에서 실행한다. 따라서 실시간 변경과 순서가 겹쳐도
commit 순서와 MongoDB CAS가 stale overwrite를 막는다.

```text
초기 투영 B: clock lock, version=40 발급, RDB 상태 조회, commit
실시간 변경 T: B commit 뒤 version=41 발급, Outbox와 함께 commit
T의 Outbox가 MongoDB version=41을 먼저 반영
B가 뒤늦게 version=40 반영 시도
-> 저장 projectionVersion < incoming 조건 불일치
-> stale 성공(no-op), 최신 상태 유지
```

반대로 실시간 변경이 먼저 version을 발급하고 commit하면 초기 투영은 더 큰 version으로
그 변경이 반영된 RDB 현재 상태를 읽는다. 원본 엔티티별 `@Version`, gap lock 또는
초기 투영 동안의 장기 RDB row lock은 사용하지 않는다.

## 실행 설정

기본값은 비활성화다. MongoDB 연결, 실시간 Outbox worker와 초기 투영을 모두 활성화하고
실행마다 새 UUID를 명시한다. 이는 UUID keyset scan 도중 cursor 앞쪽에 새 활동 row가
삽입되더라도 실시간 Outbox가 해당 변경을 빠뜨리지 않게 하기 위한 실행 전제다.

```properties
MONEW_MONGODB_ENABLED=true
MONEW_MONGODB_WORKER_ENABLED=true
MONEW_MONGODB_BACKFILL_ENABLED=true
MONEW_MONGODB_BACKFILL_RUN_ID=9f5292bf-bfca-4b9d-922a-5d2bd4a13de7
MONEW_MONGODB_BACKFILL_FIXED_DELAY_MS=1000
MONEW_MONGODB_BACKFILL_BATCH_SIZE=100
MONEW_MONGODB_BACKFILL_CLAIM_LEASE=5m
MONEW_MONGODB_BACKFILL_HEARTBEAT_INTERVAL=1m
```

`run-id` 누락, 0 이하 polling/batch 값, 0 이하 lease 또는 lease 이상 heartbeat는 시작
단계에서 커스텀 설정 예외로 거부한다. 세 활성화 값 중 하나라도 `false`이면 초기 투영
scheduler는 생성되지 않는다.

## 완료 검증

마지막 stage 후 검증기는 repeatable-read RDB snapshot에서 네 원본을 다시 keyset
scan한다. 성능 시간은 측정하지 않으며 다음 항목만 활동 유형별로 기록한다.

- 현재 RDB 기준 노출 예상 활동 수
- MongoDB의 실제 `visible=true`, `tombstone=false` 활동 수
- 결정적 `_id`, 사용자/대상/type/sourceActivityId가 다른 활동 수
- 참조 snapshot의 존재·노출 여부와 현재 count 불일치 수
- 관심사 `subscriberCount`, 댓글 `likeCount`, 기사 `viewCount/commentCount`

checkpoint의 `verification_report`에는 다음 형태의 JSON이 저장된다.

```json
{
  "stages": {
    "SUBSCRIPTION": {
      "expectedActivities": 120,
      "actualVisibleActivities": 120,
      "missingOrInvalidActivities": 0,
      "snapshotChecks": 120,
      "snapshotMismatches": 0
    },
    "COMMENT_WRITTEN": {
      "expectedActivities": 350,
      "actualVisibleActivities": 350,
      "missingOrInvalidActivities": 0,
      "snapshotChecks": 350,
      "snapshotMismatches": 0
    }
  }
}
```

실제 보고서에는 네 stage가 모두 포함된다. 어느 하나라도 예상/실제 수, 활동 핵심 필드,
snapshot 상태 또는 count가 다르면 `VERIFICATION_FAILED` 보고서를 남기고 다음 polling에서
첫 stage부터 다시 멱등 투영한다. RDB와 MongoDB를 하나의 snapshot transaction으로 묶지
않으므로 쓰기가 계속 발생하는 순간에는 일시적인 불일치가 가능하며, Outbox가 수렴한 뒤의
성공 보고서를 조회 전환 판단 근거로 사용한다.

## 구현 경계

- 조회 API는 계속 RDB를 사용한다.
- MongoDB에서 RDB로 역동기화하지 않는다.
- 복구·재노출 및 `DEAD_LETTER` 운영 재처리는 MID4-251 범위로 남긴다.
- 초기 투영 성능 측정과 전용 인덱스 추가는 수행하지 않는다.
