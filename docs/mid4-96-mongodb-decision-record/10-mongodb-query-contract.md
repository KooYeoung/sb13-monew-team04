# MongoDB 활동내역 조회 계약

[상위 문서](./README.md) | [이전: 초기 데이터 투영 및 정합성 검증](./09-initial-projection-and-reconciliation.md)

## 목적과 구현 경계

MID4-250은 MongoDB Read Model에서 기존 사용자 활동내역 DTO를 만들 수 있는 조회 계약을
구현한다. `GET /api/user-activities/{userId}`의 endpoint와 응답 필드는 바꾸지 않는다.

`UserActivityReadSource`는 활동 목록 저장소를 추상화하고 RDB와 MongoDB 구현이 같은
`UserActivitySections`를 반환한다. MID4-139는 설정 기반 router를 연결하며 기본 source는
RDB다. MongoDB source를 선택한 상태에서 조회가 실패하면 같은 요청 전체를 RDB source로
다시 조회한다. shadow read 비교는 포함하지 않는다.

사용자 존재, 이메일, 닉네임, 가입일은 계속 RDB에서 읽는다. MongoDB 구현은 다음 네
활동 목록만 담당한다.

| activity type | snapshot | 기존 DTO | 기존 API 응답 범위 |
| --- | --- | --- | --- |
| `INTEREST_SUBSCRIBED` | `interest_activity_snapshots` | `RecentSubscribed` | 현재 구독 중인 관심사 전체 |
| `COMMENT_WRITTEN` | `comment_activity_snapshots` | `RecentComment` | 최신 10건 |
| `COMMENT_LIKED` | `comment_activity_snapshots` | `RecentCommentLike` | 최신 10건 |
| `ARTICLE_VIEWED` | `article_activity_snapshots` | `RecentArticle` | 최신 10건 |

MongoDB source도 이 범위를 유지한다. 최근 활동 세 영역은 첫 page 10건만 사용한다. 구독은
내부적으로 10건씩 cursor page를 읽고 마지막 page까지 합쳐 기존 RDB와 같이 전체를
반환한다. 이 내부 cursor와 page 크기는 공개 API에 노출하지 않는다.

## activity 조회와 cursor

각 유형은 다음 조건으로 조회한다.

```text
userId = 요청 사용자
type = 요청 활동 유형
targetType = 활동 유형에 대응하는 대상 유형
visible = true
status = ACTIVE
tombstone = false
order by occurredAt desc, _id desc
```

내부 조회 요청은 `userId`가 있어야 하고 `limit`은 1 이상 `Integer.MAX_VALUE` 미만이어야
한다. 첫 페이지의 cursor는 `null`이다. 다음 페이지 cursor에는 `occurredAt`과 64자리
소문자 SHA-256 activity `_id`가 모두 있어야 한다. 사용자, limit 또는 cursor 조건이
잘못되면 `ReadModelQueryConditionInvalidException`으로 거부한다.

```text
userId=null                            -> 거부
limit=0 또는 limit=Integer.MAX_VALUE   -> 거부
cursor.occurredAt=null                 -> 거부
cursor.activityId=대문자 또는 64자리 아님 -> 거부
```

```text
다음 페이지 조건
occurredAt < cursor.occurredAt
OR (occurredAt = cursor.occurredAt AND _id < cursor.activityId)
```

예를 들어 같은 시각의 `_id`가 `ff...`, `ee...`, `dd...`이고 limit이 2이면 첫 페이지는
`ff...`, `ee...` 순서다. 여기서 `...`는 가독성을 위해 64자리 SHA-256의 나머지 문자를
생략한 표기다. 다음 cursor는 `(같은 occurredAt, ee...)`이며 다음 페이지는 `dd...`부터
시작한다. `_id` 보조 정렬 때문에 같은 발생 시각에서도 중복과 누락 없이 진행한다.

MID4-253부터 문자열 필드명을 조합하는 `Criteria` 대신 MongoDB document에서 생성한 Q 타입으로
같은 조건을 표현한다. Mongo annotation processor는 기존 JPA Querydsl, Lombok, MapStruct
processor와 함께 실행한다. `id` 경로는 Spring Data mapping을 거쳐 MongoDB `_id`로 변환된다.

```java
QActivityHistoryDocument activity = QActivityHistoryDocument.activityHistoryDocument;
BooleanExpression predicate = activity.userId.eq(request.userId().toString())
        .and(activity.type.eq(type))
        .and(activity.targetType.eq(targetType))
        .and(activity.visible.isTrue())
        .and(activity.status.eq(ActivityHistoryStatus.ACTIVE))
        .and(activity.tombstone.isFalse());

if (request.cursor() != null) {
    predicate = predicate.and(
            activity.occurredAt.lt(request.cursor().occurredAt())
                    .or(activity.occurredAt.eq(request.cursor().occurredAt())
                            .and(activity.id.lt(request.cursor().activityId())))
    );
}

List<ActivityHistoryDocument> fetched = querydsl.fetch(
        activity,
        ACTIVITY_HISTORIES,
        predicate,
        request.limit() + 1L,
        activity.occurredAt.desc(),
        activity.id.desc()
);
```

## snapshot 필터링과 page 진행

조회기는 activity를 `limit + 1`개 가져와 다음 페이지 존재 여부를 판단하고 처음
`limit`개를 이번 페이지의 스캔 후보로 확정한다. 그 후보가 참조하는 snapshot을 `_id`
목록으로 한 번에 조회한 뒤 activity 순서대로 DTO를 만든다.

```java
List<CommentActivitySnapshot> snapshots = querydsl.fetch(
        commentSnapshot,
        COMMENT_SNAPSHOTS,
        commentSnapshot.id.in(snapshotIds)
);
```

다음 snapshot은 응답에서 제외한다.

- 문서가 없음
- `visible=false`
- `tombstone=true`
- snapshot의 대상 ID와 activity `targetId`가 다름

제외된 항목을 채우려고 뒤의 activity를 추가 조회하지 않는다. cursor는 마지막 응답 DTO가
아니라 마지막 스캔 후보를 기준으로 한다.

```text
limit=2, 원본 조회=A/B/C
A snapshot=visible
B snapshot=missing
C=hasNext 확인용

응답 content=[A]
hasNext=true
nextCursor=B의 occurredAt + _id
```

B가 응답에서 빠졌어도 다음 조회는 B 이후부터 시작한다. 모든 후보가 필터링돼 content가
비어도 `hasNext=true`이면 같은 방식으로 다음 cursor를 제공한다.

구독 전체 조회는 짧거나 빈 내부 page도 건너뛰며 마지막 page까지 합친다.

```text
현재 활성 구독=12건, 내부 page 크기=10
1 page: content=10건, hasNext=true, nextCursor 있음
2 page: content=2건, hasNext=false

최종 UserActivityDto.subscriptions=12건
comments/commentLikes/articleViews=각각 첫 page 최대 10건
```

`hasNext=true`인데 다음 cursor가 없거나 이전 cursor보다 내림차순으로 진행하지 않으면
`ReadModelQueryProgressException`으로 중단한다. 이는 잘못된 page 응답으로 구독 전체 조회가
무한 반복되는 것을 막는 내부 정합성 검사다.

## 기존 DTO 매핑

activity의 `sourceActivityId`는 기존 RDB 활동 row ID다. 표시 정보와 현재 count는
snapshot에서 가져오며 시간 필드는 기존 DTO 의미에 맞춰 다음과 같이 매핑한다.

| DTO | activity에서 가져오는 값 | snapshot에서 가져오는 값 |
| --- | --- | --- |
| `RecentSubscribed` | `id=sourceActivityId`, `createdAt=occurredAt` | 관심사 표시 정보, `subscriberCount -> interestSubscriberCount` |
| `RecentComment` | `id=sourceActivityId` | 댓글·기사·작성자 표시 정보, `likeCount`, `createdAt` |
| `RecentCommentLike` | `id=sourceActivityId`, `createdAt=occurredAt` | 댓글·기사·작성자 표시 정보, `likeCount -> commentLikeCount`, `createdAt -> commentCreatedAt` |
| `RecentArticle` | `id=sourceActivityId`, `viewedBy=userId`, `viewedAt=occurredAt` | 기사 표시 정보, `commentCount -> articleCommentCount`, `viewCount -> articleViewCount` |

저장 문서의 필수 UUID를 변환할 수 없으면 `ReadModelDocumentMappingException`으로 처리한다.
snapshot 누락과 비노출은 정상적인 짧은 페이지이고 예외가 아니다. MongoDB 연결·조회
예외는 이 계층에서 숨기지 않으며 routing 계층이 RDB fallback 여부를 판단한다.

## 조회 source 전환과 fallback

조회 source는 MongoDB 기반 활성화와 별도 설정으로 선택한다. 기본값은 `RDB`다.

```properties
# 기본 조회
MONEW_ACTIVITY_READ_SOURCE=RDB
MONEW_MONGODB_ENABLED=false

# 초기 투영 완료, worker catch-up 및 정합성 확인 뒤 MongoDB 조회
MONEW_ACTIVITY_READ_SOURCE=MONGODB
MONEW_MONGODB_ENABLED=true
```

`MONEW_ACTIVITY_READ_SOURCE=MONGODB`인데 `MONEW_MONGODB_ENABLED=false`이면 잘못된 배포
설정이므로 애플리케이션 시작을 실패시킨다. MongoDB 조회로 전환하기 전에는 초기 투영
run이 `COMPLETED`인지, Outbox worker가 신규 변경을 따라잡았는지, 최종 정합성 보고가
통과했는지를 확인한다.

router의 요청별 동작은 다음과 같다.

```text
readSource=RDB
-> RDB source 조회

readSource=MONGODB, MongoDB 조회 성공
-> MongoDB 결과 반환
-> 결과가 비어 있어도 정상 성공이므로 RDB를 조회하지 않음

readSource=MONGODB, MongoDB RuntimeException 발생
-> 사용자 ID와 원인 예외를 WARN으로 기록
-> 일부 MongoDB 결과는 폐기
-> 요청 전체를 RDB source에서 다시 조회해 반환

MongoDB와 RDB 조회가 모두 실패
-> RDB 예외를 호출자에게 전달
-> 최초 MongoDB 예외는 suppressed exception으로 보존
```

fallback WARN은 다음 필드를 남기고 원인 예외의 stack trace를 함께 기록한다.

```text
WARN MongoDB 활동내역 조회에 실패해 RDB로 fallback합니다. userId=4d0ca9f8-0123-4567-89ab-0123456789ab, exceptionType=MongoTimeoutException
```

연결·조회 오류뿐 아니라 문서 매핑 및 cursor 진행 오류를 포함한 MongoDB source의 모든
`RuntimeException`을 fallback 대상으로 삼는다. JVM `Error`는 잡지 않는다. 지속 장애 시에도
각 요청은 MongoDB를 먼저 시도하며 현재 retry, circuit breaker, 일부 결과 혼합은 적용하지
않는다. 별도 fallback counter나 Micrometer metric도 제공하지 않으므로 현재는 WARN 로그를
집계해 발생 건수와 원인을 감시한다. 정상적인 빈 MongoDB 결과처럼 예외가 발생하지 않은
정합성 문제는 자동 fallback할 수 없으며 [RDB 조회 rollback 절차](../../environment-setup.md#mongodb-조회-rollback)를 사용한다.

## 제외 범위

- 공개 API에 cursor 또는 새 응답 필드 추가
- shadow read 비교
- 자동 retry와 circuit breaker
- 신규 인덱스와 성능 측정
