# 초기 데이터 투영 및 정합성 검증

[상위 문서](./README.md) | [이전: RDB 조회 성능 측정 시나리오](./08-rdb-performance-test-scenarios.md) | [다음: MongoDB 조회 계약](./10-mongodb-query-contract.md)

## 목적과 범위

MID4-249는 MongoDB 조회 경로 전환 전에 기존 RDB 활동 데이터를 Read Model로 채우고,
중단 후 재개 및 최종 정합성 확인이 가능한 실행 경로를 준비한다. MongoDB 조회 계약과
cursor 검증은 MID4-250, 실제 조회 API 전환과 fallback은 MID4-139 범위다.

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

MID4-253부터 MongoDB 검증기의 activity·snapshot ID 조건과 visible activity count 조건은
각 document Q 타입으로 구성한다. 조회는 공통 Querydsl 지원 계층에서 실행하며 검증 항목,
keyset 진행 규칙과 최종 보고서 형식은 변경하지 않는다.

```java
List<CommentActivitySnapshot> snapshots = querydsl.fetch(
        COMMENT_SNAPSHOT,
        COMMENT_SNAPSHOTS,
        COMMENT_SNAPSHOT.id.in(snapshotIds)
);

long actualVisibleActivities = querydsl.count(
        ACTIVITY,
        ACTIVITY_HISTORIES,
        ACTIVITY.type.eq(activityType(stage))
                .and(ACTIVITY.targetType.eq(targetType(stage)))
                .and(ACTIVITY.visible.isTrue())
                .and(ACTIVITY.tombstone.isFalse())
);
```

Q 타입의 `id` 경로는 Spring Data MongoDB mapping에서 `_id`로 변환된다.
`actualVisibleActivities`는 이름 그대로 `visible=true`, `tombstone=false`인 실제 문서 수를
센다. `status!=ACTIVE`처럼 문서는 노출 상태지만 필드 계약이 잘못된 경우는 count에서
제외하지 않고, 개별 문서 검증의 `missingOrInvalidActivities`로 별도 집계한다.

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

```text
인스턴스 A: claim_id=CA, pending_last_id=D, claim_until=10:05로 page claim
인스턴스 A: B/C를 반영한 뒤 중단되어 heartbeat 종료
인스턴스 B: DB 시각 10:05 전에는 같은 run-id를 claim하지 못함
인스턴스 B: DB 시각 10:05 이후 claim_id=CB로 같은 완료 cursor 다음 범위를 회수
-> B/C/D를 다시 반영하고 성공한 뒤 last_processed_id=D로 이동
-> 늦게 돌아온 A의 CA 기반 상태 저장은 RMB_003으로 거부
```

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

### 운영 실행과 재개

실행마다 새 UUID를 생성해 모든 인스턴스에 같은 값으로 설정한다. Windows PowerShell과
Mac/Linux에서는 각각 다음처럼 생성할 수 있다.

```powershell
[guid]::NewGuid().ToString()
```

```bash
uuidgen
```

애플리케이션을 시작한 뒤에는 다음 조회로 진행 상태를 확인한다.

```sql
SELECT run_id,
       stage,
       status,
       last_processed_id,
       pending_last_id,
       processed_count,
       retry_count,
       last_error,
       verified_at
FROM read_model_backfill_runs
WHERE run_id = '9f5292bf-bfca-4b9d-922a-5d2bd4a13de7';
```

| 관찰한 상태 | 운영 동작 |
| --- | --- |
| `RUNNING` 또는 `VERIFYING` | 현재 claim과 heartbeat가 끝날 때까지 기다린다. |
| `FAILED` | 같은 run-id를 유지하면 다음 polling에서 완료 cursor 이후 또는 pending 범위를 자동 재시도한다. |
| `VERIFICATION_FAILED` | 보고서가 있으면 불일치 내용을 확인하며 첫 stage부터 재투영한다. 보고서가 없으면 검증 claim 시작 실패 여부를 로그에서 확인한다. |
| `COMPLETED` | backfill만 비활성화하고 Outbox worker는 계속 활성화한다. 같은 run-id는 다시 처리되지 않는다. |

Backfill 실패에는 별도 backoff, 최대 retry 횟수 또는 `DEAD_LETTER` 전이가 없다. 실패할
때마다 fixed delay 다음 polling에서 다시 시도한다. claim 이후 page 처리 또는 검증 실패가
`FAILED`로 저장되면 `retry_count`가 누적되지만, claim 획득이나 heartbeat 시작 단계의
실패는 checkpoint보다 애플리케이션 로그에만 원인이 남을 수 있다. 반복 실패를 멈추려면
`MONEW_MONGODB_BACKFILL_ENABLED=false`로 애플리케이션을 재시작한 뒤 `last_error`와 로그를
함께 확인한다. 장애 후 이어서 처리할 때는 기존 run-id를 유지하고, 완료된 범위를 포함해
전체를 새로 실행할 때만 새 UUID를 사용한다.

### 실패 코드

| 코드 | 의미 |
| --- | --- |
| `RMB_001` | run-id, polling, batch, lease 또는 heartbeat 설정이 유효하지 않음 |
| `RMB_002` | 현재 checkpoint status에서 허용되지 않는 상태 전이를 시도함 |
| `RMB_003` | 만료·회수 등으로 현재 claim UUID의 소유권을 잃음 |
| `RMB_004` | heartbeat 예약 또는 lease 갱신에 실패함 |
| `RMB_005` | 정합성 보고서를 JSON으로 직렬화하지 못함 |
| `RMB_006` | 생성 또는 잠금 조회 후 checkpoint row를 찾지 못함 |
| `RMB_007` | 검증 page의 cursor 진행 불변식이 깨짐 |

`RMB_007`의 `reason`은 `LAST_CURSOR_MISSING`, `LAST_EVENT_CURSOR_MISMATCH`,
`CURSOR_NOT_CHANGED`, `CURSOR_REPEATED` 중 하나다. 예외로 실패한 실행은 `FAILED`로
기록되어 기존 run-id로 재시도한다. 정상적으로 계산한 검증 보고서가 불일치한 경우에만
`VERIFICATION_FAILED`로 전환해 첫 stage부터 다시 투영한다.

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
    },
    "COMMENT_LIKED": {
      "expectedActivities": 480,
      "actualVisibleActivities": 480,
      "missingOrInvalidActivities": 0,
      "snapshotChecks": 480,
      "snapshotMismatches": 0
    },
    "ARTICLE_VIEWED": {
      "expectedActivities": 900,
      "actualVisibleActivities": 900,
      "missingOrInvalidActivities": 0,
      "snapshotChecks": 900,
      "snapshotMismatches": 0
    }
  }
}
```

어느 하나라도 예상/실제 수, 활동 핵심 필드, snapshot 상태 또는 count가 다르면, 예를
들어 보고서의 `stages.ARTICLE_VIEWED` 값이 다음과 같이 기록될 수 있다.

```json
{
  "expectedActivities": 900,
  "actualVisibleActivities": 899,
  "missingOrInvalidActivities": 1,
  "snapshotChecks": 900,
  "snapshotMismatches": 1
}
```

실제 `verification_report`에는 이 항목을 포함한 네 stage가 모두 저장된다. 보고서가
불일치하면 checkpoint는 다음 상태로 바뀌고, 보고서와 검증 시각은 원인 확인을 위해
유지된다.

```text
status = VERIFICATION_FAILED
stage = SUBSCRIPTION
last_processed_id = null
pending_last_id = null
processed_count = 0
verification_report = 불일치 보고서 유지
verified_at = 마지막 검증 시각 유지
```

다음 polling은 첫 stage부터 다시 멱등 투영한다. RDB와 MongoDB를 하나의 snapshot
transaction으로 묶지 않으므로 쓰기가 계속 발생하는 순간에는 일시적인 불일치가 가능하며,
Outbox가 수렴한 뒤의 성공 보고서를 조회 전환 판단 근거로 사용한다.

## 구현 경계

- 조회 API는 계속 RDB를 사용한다.
- MongoDB에서 RDB로 역동기화하지 않는다.
- 복구·재노출 및 `DEAD_LETTER` 운영 재처리는 MID4-251 범위로 남긴다.
- 초기 투영 성능 측정과 전용 인덱스 추가는 수행하지 않는다.
