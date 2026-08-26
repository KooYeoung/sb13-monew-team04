# 구독 중인 관심사

## 해석

이 문서의 `100k`, `1m`, `10m`은 각 테이블 row 수가 아니라 `seed_activity_history(scale_count)`에 전달한 seed scale이다. 실제 테이블별 row count는 README의 Seed 결과 표를 기준으로 본다.

구독 중인 관심사 조회는 `MID4-92`의 최종 조회 로직 기준으로 `subscriptions.created_at DESC, subscriptions.id DESC` 정렬을 포함한다. 대상 사용자의 구독은 50건이지만, main query에서 `subscriptions.user_id` 조건을 처리할 인덱스가 없어 seed scale이 커질수록 `Seq Scan` 또는 `Parallel Seq Scan`이 발생한다.

현재 `(interest_id, user_id)` unique index는 관심사별 구독자 수 subquery에는 사용되지만, 특정 사용자의 구독 목록을 찾는 main query에는 선두 컬럼이 맞지 않아 직접 사용되지 않는다. 100k와 10m seed scale 실행계획을 보면 정렬 노드의 비용보다 `subscriptions.user_id` 조건을 full scan으로 처리하는 비용이 더 명확한 병목으로 보인다. 정렬 추가 전 baseline과 비교해도 예측 비용과 실행 시간이 크게 벌어지지 않아, 현재 단계에서는 정렬 자체를 큰 병목으로 보기는 어렵다.

keywords batch 조회는 Hibernate가 PostgreSQL에서 `interest_id = any (?)` 형태로 실행한다. `uk_keywords_interest_keyword(interest_id, keyword)`는 조회 조건의 선두 컬럼과 맞으므로 `1m`, `10m` seed scale에서는 사용됐다. `100k` seed scale에서는 keywords row 수가 작아 planner가 `Seq Scan`을 선택했다.

## 인덱스 후보

- 1차 병목은 `subscriptions.user_id` 접근 경로 부재로 본다.
- 현재 SQL 기준 1차 후보는 `subscriptions(user_id, created_at DESC, id DESC)`로 둔다. 이 후보는 `user_id` 접근 경로를 만들면서 정렬도 함께 처리할 수 있는지 확인하기 위한 후보이지, 정렬 자체가 주요 병목이라는 의미는 아니다.
- 정렬 추가 전 후보였던 `subscriptions(user_id)`와 `subscriptions(user_id, interest_id)`는 현재 최종 후보라기보다 용량 증가량과 개선 폭을 비교하기 위한 후보로 본다.
- `subscriptions(user_id)`만으로 full scan이 대부분 제거되고 정렬 비용이 계속 작게 유지된다면, 정렬 포함 복합 인덱스와 인덱스 용량을 비교해 선택한다.
- `subscriptions(user_id, interest_id)`는 단순 FK 인덱스 대비 join 비용이나 heap 접근이 의미 있게 줄어드는지 확인하는 비교 후보로 둔다.
- 단, `uk_subscriptions_interest_user(interest_id, user_id)`는 중복 구독 방지와 관심사별 구독자 수 subquery에 필요하므로 제거 후보로 보지 않는다.
- keywords query는 현재 unique index의 선두 컬럼을 정상적으로 사용할 수 있으므로 신규 인덱스 후보로 두지 않는다.
- `users.deleted_at` 인덱스는 1차 후보로 두지 않고, `subscriptions.user_id` 접근 경로 개선 후 subquery 비용이 남는 경우 추가 측정한다.

## 측정 SQL

### 구독 중인 관심사

`MID4-92`의 `SubscribeRepository.findSubscribedInterestActivities()` JPQL을 임시 Spring Boot test에서 실행하고 Hibernate SQL 로그로 캡처한 실제 main query다. 아래 SQL은 측정을 위해 `userId` binding만 literal로 치환했다.

```sql
SELECT
    s1_0.id,
    s1_0.created_at,
    i1_0.id,
    i1_0.created_at,
    i1_0.name,
    i1_0.updated_at,
    (
        SELECT COUNT(s2_0.id)
        FROM subscriptions s2_0
        JOIN users u2_0 ON s2_0.user_id = u2_0.id
        JOIN interests i2_0 ON i2_0.id = s2_0.interest_id
        WHERE i2_0.id = i1_0.id
          AND u2_0.deleted_at IS NULL
    ) AS interest_subscriber_count
FROM subscriptions s1_0
JOIN interests i1_0 ON i1_0.id = s1_0.interest_id
JOIN users u1_0 ON u1_0.id = s1_0.user_id
WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001'
  AND u1_0.deleted_at IS NULL
ORDER BY s1_0.created_at DESC, s1_0.id DESC;
```

`SubscribedInterestActivity.from()`에서 `interest.getKeywords()`를 호출하므로 요청 1건 기준 키워드 batch 조회도 함께 측정했다. 실제 Hibernate SQL은 `IN` list가 아니라 PostgreSQL array binding인 `interest_id = any (?)` 형태로 실행됐다. binding 값은 `00000002-0000-4000-8000-000000000001`부터 `00000002-0000-4000-8000-000000000032`까지 50개 관심사 id다. 아래 SQL은 측정을 위해 같은 binding을 `ARRAY[...]` literal로 치환했다.

```sql
SELECT
    k1_0.interest_id,
    k1_0.id,
    k1_0.keyword
FROM keywords k1_0
WHERE k1_0.interest_id = ANY (ARRAY[
    '00000002-0000-4000-8000-000000000001'::uuid,
    '00000002-0000-4000-8000-000000000002'::uuid,
    '00000002-0000-4000-8000-000000000003'::uuid,
    '00000002-0000-4000-8000-000000000004'::uuid,
    '00000002-0000-4000-8000-000000000005'::uuid,
    '00000002-0000-4000-8000-000000000006'::uuid,
    '00000002-0000-4000-8000-000000000007'::uuid,
    '00000002-0000-4000-8000-000000000008'::uuid,
    '00000002-0000-4000-8000-000000000009'::uuid,
    '00000002-0000-4000-8000-00000000000a'::uuid,
    '00000002-0000-4000-8000-00000000000b'::uuid,
    '00000002-0000-4000-8000-00000000000c'::uuid,
    '00000002-0000-4000-8000-00000000000d'::uuid,
    '00000002-0000-4000-8000-00000000000e'::uuid,
    '00000002-0000-4000-8000-00000000000f'::uuid,
    '00000002-0000-4000-8000-000000000010'::uuid,
    '00000002-0000-4000-8000-000000000011'::uuid,
    '00000002-0000-4000-8000-000000000012'::uuid,
    '00000002-0000-4000-8000-000000000013'::uuid,
    '00000002-0000-4000-8000-000000000014'::uuid,
    '00000002-0000-4000-8000-000000000015'::uuid,
    '00000002-0000-4000-8000-000000000016'::uuid,
    '00000002-0000-4000-8000-000000000017'::uuid,
    '00000002-0000-4000-8000-000000000018'::uuid,
    '00000002-0000-4000-8000-000000000019'::uuid,
    '00000002-0000-4000-8000-00000000001a'::uuid,
    '00000002-0000-4000-8000-00000000001b'::uuid,
    '00000002-0000-4000-8000-00000000001c'::uuid,
    '00000002-0000-4000-8000-00000000001d'::uuid,
    '00000002-0000-4000-8000-00000000001e'::uuid,
    '00000002-0000-4000-8000-00000000001f'::uuid,
    '00000002-0000-4000-8000-000000000020'::uuid,
    '00000002-0000-4000-8000-000000000021'::uuid,
    '00000002-0000-4000-8000-000000000022'::uuid,
    '00000002-0000-4000-8000-000000000023'::uuid,
    '00000002-0000-4000-8000-000000000024'::uuid,
    '00000002-0000-4000-8000-000000000025'::uuid,
    '00000002-0000-4000-8000-000000000026'::uuid,
    '00000002-0000-4000-8000-000000000027'::uuid,
    '00000002-0000-4000-8000-000000000028'::uuid,
    '00000002-0000-4000-8000-000000000029'::uuid,
    '00000002-0000-4000-8000-00000000002a'::uuid,
    '00000002-0000-4000-8000-00000000002b'::uuid,
    '00000002-0000-4000-8000-00000000002c'::uuid,
    '00000002-0000-4000-8000-00000000002d'::uuid,
    '00000002-0000-4000-8000-00000000002e'::uuid,
    '00000002-0000-4000-8000-00000000002f'::uuid,
    '00000002-0000-4000-8000-000000000030'::uuid,
    '00000002-0000-4000-8000-000000000031'::uuid,
    '00000002-0000-4000-8000-000000000032'::uuid
]);
```

## 실행 시간

| Seed scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 구독 중인 관심사 main | `5.517 ms` | `3.951`, `3.803`, `4.980`, `4.414`, `4.327 ms` | `4.327 ms` |
| `100k` | 구독 관심사 keywords | `0.099 ms` | `0.538`, `0.449`, `0.446`, `0.450`, `0.517 ms` | `0.450 ms` |
| `100k` | 구독 중인 관심사 total | `-` | `4.472`, `4.481`, `4.332`, `4.380`, `5.195 ms` | `4.472 ms` |
| `1m` | 구독 중인 관심사 main | `2.666 ms` | `2.961`, `2.884`, `2.772`, `2.762`, `2.634 ms` | `2.772 ms` |
| `1m` | 구독 관심사 keywords | `0.047 ms` | `0.451`, `0.464`, `0.572`, `0.585`, `0.292 ms` | `0.464 ms` |
| `1m` | 구독 중인 관심사 total | `-` | `4.708`, `4.280`, `3.549`, `3.453`, `3.331 ms` | `3.549 ms` |
| `10m` | 구독 중인 관심사 main | `10.052 ms` | `11.051`, `10.541`, `10.535`, `10.878`, `10.810 ms` | `10.810 ms` |
| `10m` | 구독 관심사 keywords | `0.050 ms` | `0.517`, `0.391`, `0.496`, `0.596`, `0.486 ms` | `0.496 ms` |
| `10m` | 구독 중인 관심사 total | `-` | `11.635`, `11.394`, `12.454`, `14.413`, `11.481 ms` | `11.635 ms` |

## EXPLAIN 실행계획 요약

### 100k

구독 중인 관심사:

- main query는 `subscriptions.user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `4995`
- `ORDER BY s1_0.created_at DESC, s1_0.id DESC`는 `Sort Method: quicksort`로 처리했지만, 정렬 대상이 50건이라 정렬 자체보다 `subscriptions.user_id` full scan이 더 명확한 비용으로 보인다.
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user`를 `interest_id = ...` 조건으로 사용했다.
- subquery 내부 활성 사용자 확인은 `users`를 50회 `Seq Scan`했다.
- keywords query는 `interest_id = any (...)` 조건을 `Seq Scan`으로 처리했다. 작은 scale에서는 planner가 index scan보다 sequential scan을 선택했다.

### 1m

구독 중인 관심사:

- main query는 `subscriptions.user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `49995`
- `ORDER BY s1_0.created_at DESC, s1_0.id DESC`는 `Sort Method: quicksort`로 처리했다.
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user`를 `interest_id = ...` 조건으로 사용했다.
- subquery 내부 활성 사용자 확인은 `pk_users`를 사용했다.
- keywords query는 `uk_keywords_interest_keyword`를 `interest_id = any (...)` 조건으로 사용했다.

### 10m

구독 중인 관심사:

- main query는 `subscriptions.user_id` 조건을 `Parallel Seq Scan`으로 처리했다.
- worker별 `Rows Removed by Filter`: `166665`, `loops=3`
- `ORDER BY s1_0.created_at DESC, s1_0.id DESC`는 worker별 `Sort`와 `Gather Merge`로 처리했지만, 실행계획상 핵심 병목은 정렬보다 `subscriptions.user_id` 접근 경로 부재로 본다.
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user`를 `interest_id = ...` 조건으로 사용했다.
- keywords query는 `uk_keywords_interest_keyword`를 `interest_id = any (...)` 조건으로 사용했다.
- 요청 1건 기준 total median은 `11.635 ms`다. 현재 seed scale에서는 다른 최근 활동 조회보다 낮지만, main query가 전체 subscriptions를 훑는 구조라 데이터 증가 시 병목 후보로 남는다.

## EXPLAIN 실행계획 주요 원문

아래 원문에서 keywords의 `ANY` 배열은 실제 측정에 사용한 50개 관심사 id 전체를 그대로 기록했다.

<details>
<summary>100k - 구독 중인 관심사</summary>

main query:

```text
Sort  (cost=3568.96..3569.09 rows=50 width=76) (actual time=5.482..5.484 rows=50 loops=1)
  Sort Key: s1_0.created_at DESC, s1_0.id DESC
  Sort Method: quicksort  Memory: 30kB
  Buffers: shared hit=1609
  ->  Nested Loop  (cost=116.96..3567.55 rows=50 width=76) (actual time=0.328..5.467 rows=50 loops=1)
        Buffers: shared hit=1609
        ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.005..0.005 rows=1 loops=1)
              Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (deleted_at IS NULL)
              Buffers: shared hit=3
        ->  Hash Join  (cost=116.69..128.01 rows=50 width=84) (actual time=0.182..0.236 rows=50 loops=1)
              Hash Cond: (i1_0.id = s1_0.interest_id)
              Buffers: shared hit=58
              ->  Seq Scan on interests i1_0  (cost=0.00..10.00 rows=500 width=44) (actual time=0.002..0.024 rows=500 loops=1)
                    Buffers: shared hit=5
              ->  Hash  (cost=116.06..116.06 rows=50 width=56) (actual time=0.175..0.175 rows=50 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 13kB
                    Buffers: shared hit=53
                    ->  Seq Scan on subscriptions s1_0  (cost=0.00..116.06 rows=50 width=56) (actual time=0.002..0.170 rows=50 loops=1)
                          Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Rows Removed by Filter: 4995
                          Buffers: shared hit=53
  SubPlan 1
    ->  Aggregate  (cost=68.60..68.61 rows=1 width=8) (actual time=0.104..0.104 rows=1 loops=50)
          Buffers: shared hit=1548
          ->  Nested Loop  (cost=31.85..68.58 rows=10 width=16) (actual time=0.008..0.103 rows=11 loops=50)
                Buffers: shared hit=1548
                ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.27..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Index Cond: (id = i1_0.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Hash Join  (cost=31.58..60.19 rows=10 width=32) (actual time=0.007..0.101 rows=11 loops=50)
                      Hash Cond: (u2_0.id = s2_0.user_id)
                      Buffers: shared hit=1398
                      ->  Seq Scan on users u2_0  (cost=0.00..26.00 rows=990 width=16) (actual time=0.001..0.054 rows=990 loops=50)
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 10
                            Buffers: shared hit=800
                      ->  Hash  (cost=31.45..31.45 rows=10 width=48) (actual time=0.005..0.005 rows=11 loops=50)
                            Buckets: 1024  Batches: 1  Memory Usage: 9kB
                            Buffers: shared hit=598
                            ->  Bitmap Heap Scan on subscriptions s2_0  (cost=4.36..31.45 rows=10 width=48) (actual time=0.002..0.004 rows=11 loops=50)
                                  Recheck Cond: (interest_id = i1_0.id)
                                  Heap Blocks: exact=498
                                  Buffers: shared hit=598
                                  ->  Bitmap Index Scan on uk_subscriptions_interest_user  (cost=0.00..4.36 rows=10 width=0) (actual time=0.001..0.001 rows=11 loops=50)
                                        Index Cond: (interest_id = i1_0.id)
                                        Buffers: shared hit=100
Planning:
  Buffers: shared hit=18
Planning Time: 0.213 ms
Execution Time: 5.517 ms
```

keywords query:

```text
Seq Scan on keywords k1_0  (cost=0.12..37.62 rows=150 width=45) (actual time=0.005..0.090 rows=150 loops=1)
  Filter: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,00000002-0000-4000-8000-000000000002,00000002-0000-4000-8000-000000000003,00000002-0000-4000-8000-000000000004,00000002-0000-4000-8000-000000000005,00000002-0000-4000-8000-000000000006,00000002-0000-4000-8000-000000000007,00000002-0000-4000-8000-000000000008,00000002-0000-4000-8000-000000000009,00000002-0000-4000-8000-00000000000a,00000002-0000-4000-8000-00000000000b,00000002-0000-4000-8000-00000000000c,00000002-0000-4000-8000-00000000000d,00000002-0000-4000-8000-00000000000e,00000002-0000-4000-8000-00000000000f,00000002-0000-4000-8000-000000000010,00000002-0000-4000-8000-000000000011,00000002-0000-4000-8000-000000000012,00000002-0000-4000-8000-000000000013,00000002-0000-4000-8000-000000000014,00000002-0000-4000-8000-000000000015,00000002-0000-4000-8000-000000000016,00000002-0000-4000-8000-000000000017,00000002-0000-4000-8000-000000000018,00000002-0000-4000-8000-000000000019,00000002-0000-4000-8000-00000000001a,00000002-0000-4000-8000-00000000001b,00000002-0000-4000-8000-00000000001c,00000002-0000-4000-8000-00000000001d,00000002-0000-4000-8000-00000000001e,00000002-0000-4000-8000-00000000001f,00000002-0000-4000-8000-000000000020,00000002-0000-4000-8000-000000000021,00000002-0000-4000-8000-000000000022,00000002-0000-4000-8000-000000000023,00000002-0000-4000-8000-000000000024,00000002-0000-4000-8000-000000000025,00000002-0000-4000-8000-000000000026,00000002-0000-4000-8000-000000000027,00000002-0000-4000-8000-000000000028,00000002-0000-4000-8000-000000000029,00000002-0000-4000-8000-00000000002a,00000002-0000-4000-8000-00000000002b,00000002-0000-4000-8000-00000000002c,00000002-0000-4000-8000-00000000002d,00000002-0000-4000-8000-00000000002e,00000002-0000-4000-8000-00000000002f,00000002-0000-4000-8000-000000000030,00000002-0000-4000-8000-000000000031,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Rows Removed by Filter: 1350
  Buffers: shared hit=15
Planning Time: 0.070 ms
Execution Time: 0.099 ms
```

</details>

<details>
<summary>1m - 구독 중인 관심사</summary>

main query:

```text
Sort  (cost=6122.16..6122.25 rows=38 width=77) (actual time=2.635..2.638 rows=50 loops=1)
  Sort Key: s1_0.created_at DESC, s1_0.id DESC
  Sort Method: quicksort  Memory: 30kB
  Buffers: shared hit=2999
  ->  Nested Loop  (cost=1142.32..6121.16 rows=38 width=77) (actual time=1.459..2.622 rows=50 loops=1)
        Buffers: shared hit=2999
        ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.004..0.005 rows=1 loops=1)
              Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (deleted_at IS NULL)
              Buffers: shared hit=3
        ->  Hash Join  (cost=1142.04..1252.17 rows=38 width=85) (actual time=1.411..1.833 rows=50 loops=1)
              Hash Cond: (i1_0.id = s1_0.interest_id)
              Buffers: shared hit=563
              ->  Seq Scan on interests i1_0  (cost=0.00..97.00 rows=5000 width=45) (actual time=0.001..0.213 rows=5000 loops=1)
                    Buffers: shared hit=47
              ->  Hash  (cost=1141.56..1141.56 rows=38 width=56) (actual time=1.406..1.406 rows=50 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 13kB
                    Buffers: shared hit=516
                    ->  Seq Scan on subscriptions s1_0  (cost=0.00..1141.56 rows=38 width=56) (actual time=0.002..1.401 rows=50 loops=1)
                          Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Rows Removed by Filter: 49995
                          Buffers: shared hit=516
  SubPlan 1
    ->  Aggregate  (cost=127.89..127.90 rows=1 width=8) (actual time=0.015..0.015 rows=1 loops=50)
          Buffers: shared hit=2433
          ->  Nested Loop  (cost=5.06..127.87 rows=10 width=16) (actual time=0.004..0.015 rows=11 loops=50)
                Buffers: shared hit=2433
                ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.28..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Index Cond: (id = i1_0.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Nested Loop  (cost=4.78..119.47 rows=10 width=32) (actual time=0.003..0.013 rows=11 loops=50)
                      Buffers: shared hit=2283
                      ->  Bitmap Heap Scan on subscriptions s2_0  (cost=4.49..40.44 rows=10 width=48) (actual time=0.002..0.005 rows=11 loops=50)
                            Recheck Cond: (interest_id = i1_0.id)
                            Heap Blocks: exact=498
                            Buffers: shared hit=648
                            ->  Bitmap Index Scan on uk_subscriptions_interest_user  (cost=0.00..4.49 rows=10 width=0) (actual time=0.001..0.001 rows=11 loops=50)
                                  Index Cond: (interest_id = i1_0.id)
                                  Buffers: shared hit=150
                      ->  Index Scan using pk_users on users u2_0  (cost=0.29..7.90 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=545)
                            Index Cond: (id = s2_0.user_id)
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 0
                            Buffers: shared hit=1635
Planning:
  Buffers: shared hit=20
Planning Time: 0.183 ms
Execution Time: 2.666 ms
```

keywords query:

```text
Bitmap Heap Scan on keywords k1_0  (cost=183.54..332.68 rows=150 width=46) (actual time=0.025..0.035 rows=150 loops=1)
  Recheck Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,00000002-0000-4000-8000-000000000002,00000002-0000-4000-8000-000000000003,00000002-0000-4000-8000-000000000004,00000002-0000-4000-8000-000000000005,00000002-0000-4000-8000-000000000006,00000002-0000-4000-8000-000000000007,00000002-0000-4000-8000-000000000008,00000002-0000-4000-8000-000000000009,00000002-0000-4000-8000-00000000000a,00000002-0000-4000-8000-00000000000b,00000002-0000-4000-8000-00000000000c,00000002-0000-4000-8000-00000000000d,00000002-0000-4000-8000-00000000000e,00000002-0000-4000-8000-00000000000f,00000002-0000-4000-8000-000000000010,00000002-0000-4000-8000-000000000011,00000002-0000-4000-8000-000000000012,00000002-0000-4000-8000-000000000013,00000002-0000-4000-8000-000000000014,00000002-0000-4000-8000-000000000015,00000002-0000-4000-8000-000000000016,00000002-0000-4000-8000-000000000017,00000002-0000-4000-8000-000000000018,00000002-0000-4000-8000-000000000019,00000002-0000-4000-8000-00000000001a,00000002-0000-4000-8000-00000000001b,00000002-0000-4000-8000-00000000001c,00000002-0000-4000-8000-00000000001d,00000002-0000-4000-8000-00000000001e,00000002-0000-4000-8000-00000000001f,00000002-0000-4000-8000-000000000020,00000002-0000-4000-8000-000000000021,00000002-0000-4000-8000-000000000022,00000002-0000-4000-8000-000000000023,00000002-0000-4000-8000-000000000024,00000002-0000-4000-8000-000000000025,00000002-0000-4000-8000-000000000026,00000002-0000-4000-8000-000000000027,00000002-0000-4000-8000-000000000028,00000002-0000-4000-8000-000000000029,00000002-0000-4000-8000-00000000002a,00000002-0000-4000-8000-00000000002b,00000002-0000-4000-8000-00000000002c,00000002-0000-4000-8000-00000000002d,00000002-0000-4000-8000-00000000002e,00000002-0000-4000-8000-00000000002f,00000002-0000-4000-8000-000000000030,00000002-0000-4000-8000-000000000031,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Heap Blocks: exact=4
  Buffers: shared hit=104
  ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..183.38 rows=150 width=0) (actual time=0.022..0.023 rows=150 loops=1)
        Index Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,00000002-0000-4000-8000-000000000002,00000002-0000-4000-8000-000000000003,00000002-0000-4000-8000-000000000004,00000002-0000-4000-8000-000000000005,00000002-0000-4000-8000-000000000006,00000002-0000-4000-8000-000000000007,00000002-0000-4000-8000-000000000008,00000002-0000-4000-8000-000000000009,00000002-0000-4000-8000-00000000000a,00000002-0000-4000-8000-00000000000b,00000002-0000-4000-8000-00000000000c,00000002-0000-4000-8000-00000000000d,00000002-0000-4000-8000-00000000000e,00000002-0000-4000-8000-00000000000f,00000002-0000-4000-8000-000000000010,00000002-0000-4000-8000-000000000011,00000002-0000-4000-8000-000000000012,00000002-0000-4000-8000-000000000013,00000002-0000-4000-8000-000000000014,00000002-0000-4000-8000-000000000015,00000002-0000-4000-8000-000000000016,00000002-0000-4000-8000-000000000017,00000002-0000-4000-8000-000000000018,00000002-0000-4000-8000-000000000019,00000002-0000-4000-8000-00000000001a,00000002-0000-4000-8000-00000000001b,00000002-0000-4000-8000-00000000001c,00000002-0000-4000-8000-00000000001d,00000002-0000-4000-8000-00000000001e,00000002-0000-4000-8000-00000000001f,00000002-0000-4000-8000-000000000020,00000002-0000-4000-8000-000000000021,00000002-0000-4000-8000-000000000022,00000002-0000-4000-8000-000000000023,00000002-0000-4000-8000-000000000024,00000002-0000-4000-8000-000000000025,00000002-0000-4000-8000-000000000026,00000002-0000-4000-8000-000000000027,00000002-0000-4000-8000-000000000028,00000002-0000-4000-8000-000000000029,00000002-0000-4000-8000-00000000002a,00000002-0000-4000-8000-00000000002b,00000002-0000-4000-8000-00000000002c,00000002-0000-4000-8000-00000000002d,00000002-0000-4000-8000-00000000002e,00000002-0000-4000-8000-00000000002f,00000002-0000-4000-8000-000000000030,00000002-0000-4000-8000-000000000031,00000002-0000-4000-8000-000000000032}'::uuid[]))
        Buffers: shared hit=100
Planning Time: 0.071 ms
Execution Time: 0.047 ms
```

</details>

<details>
<summary>10m - 구독 중인 관심사</summary>

main query:

```text
Nested Loop  (cost=8777.47..9472.10 rows=5 width=78) (actual time=8.779..9.997 rows=50 loops=1)
  Buffers: shared hit=3544 read=4820
  ->  Gather Merge  (cost=8777.05..8777.63 rows=5 width=86) (actual time=8.692..9.227 rows=50 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        Buffers: shared hit=516 read=4820
        ->  Sort  (cost=7777.03..7777.03 rows=2 width=86) (actual time=7.014..7.016 rows=17 loops=3)
              Sort Key: s1_0.created_at DESC, s1_0.id DESC
              Sort Method: quicksort  Memory: 30kB
              Buffers: shared hit=516 read=4820
              Worker 0:  Sort Method: quicksort  Memory: 25kB
              Worker 1:  Sort Method: quicksort  Memory: 25kB
              ->  Nested Loop  (cost=0.29..7777.02 rows=2 width=86) (actual time=4.173..6.987 rows=17 loops=3)
                    Buffers: shared hit=486 read=4820
                    ->  Parallel Seq Scan on subscriptions s1_0  (cost=0.00..7760.40 rows=2 width=56) (actual time=4.169..6.972 rows=17 loops=3)
                          Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Rows Removed by Filter: 166665
                          Buffers: shared hit=336 read=4820
                    ->  Index Scan using pk_interests on interests i1_0  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
                          Index Cond: (id = s1_0.interest_id)
                          Buffers: shared hit=150
  ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=50)
        Buffers: shared hit=4
        ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.014..0.015 rows=1 loops=1)
              Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (deleted_at IS NULL)
              Buffers: shared hit=4
  SubPlan 1
    ->  Aggregate  (cost=137.18..137.19 rows=1 width=8) (actual time=0.014..0.014 rows=1 loops=50)
          Buffers: shared hit=3024
          ->  Nested Loop  (cost=1.13..137.16 rows=10 width=16) (actual time=0.003..0.014 rows=11 loops=50)
                Buffers: shared hit=3024
                ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.29..8.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Index Cond: (id = i1_0.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Nested Loop  (cost=0.84..128.75 rows=10 width=32) (actual time=0.002..0.012 rows=11 loops=50)
                      Buffers: shared hit=2874
                      ->  Index Scan using uk_subscriptions_interest_user on subscriptions s2_0  (cost=0.42..44.40 rows=10 width=48) (actual time=0.001..0.003 rows=11 loops=50)
                            Index Cond: (interest_id = i1_0.id)
                            Buffers: shared hit=694
                      ->  Index Scan using pk_users on users u2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=545)
                            Index Cond: (id = s2_0.user_id)
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 0
                            Buffers: shared hit=2180
Planning:
  Buffers: shared hit=22
Planning Time: 0.298 ms
Execution Time: 10.052 ms
```

keywords query:

```text
Bitmap Heap Scan on keywords k1_0  (cost=218.29..655.53 rows=150 width=47) (actual time=0.029..0.038 rows=150 loops=1)
  Recheck Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,00000002-0000-4000-8000-000000000002,00000002-0000-4000-8000-000000000003,00000002-0000-4000-8000-000000000004,00000002-0000-4000-8000-000000000005,00000002-0000-4000-8000-000000000006,00000002-0000-4000-8000-000000000007,00000002-0000-4000-8000-000000000008,00000002-0000-4000-8000-000000000009,00000002-0000-4000-8000-00000000000a,00000002-0000-4000-8000-00000000000b,00000002-0000-4000-8000-00000000000c,00000002-0000-4000-8000-00000000000d,00000002-0000-4000-8000-00000000000e,00000002-0000-4000-8000-00000000000f,00000002-0000-4000-8000-000000000010,00000002-0000-4000-8000-000000000011,00000002-0000-4000-8000-000000000012,00000002-0000-4000-8000-000000000013,00000002-0000-4000-8000-000000000014,00000002-0000-4000-8000-000000000015,00000002-0000-4000-8000-000000000016,00000002-0000-4000-8000-000000000017,00000002-0000-4000-8000-000000000018,00000002-0000-4000-8000-000000000019,00000002-0000-4000-8000-00000000001a,00000002-0000-4000-8000-00000000001b,00000002-0000-4000-8000-00000000001c,00000002-0000-4000-8000-00000000001d,00000002-0000-4000-8000-00000000001e,00000002-0000-4000-8000-00000000001f,00000002-0000-4000-8000-000000000020,00000002-0000-4000-8000-000000000021,00000002-0000-4000-8000-000000000022,00000002-0000-4000-8000-000000000023,00000002-0000-4000-8000-000000000024,00000002-0000-4000-8000-000000000025,00000002-0000-4000-8000-000000000026,00000002-0000-4000-8000-000000000027,00000002-0000-4000-8000-000000000028,00000002-0000-4000-8000-000000000029,00000002-0000-4000-8000-00000000002a,00000002-0000-4000-8000-00000000002b,00000002-0000-4000-8000-00000000002c,00000002-0000-4000-8000-00000000002d,00000002-0000-4000-8000-00000000002e,00000002-0000-4000-8000-00000000002f,00000002-0000-4000-8000-000000000030,00000002-0000-4000-8000-000000000031,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Heap Blocks: exact=4
  Buffers: shared hit=154
  ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..218.12 rows=150 width=0) (actual time=0.026..0.027 rows=150 loops=1)
        Index Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,00000002-0000-4000-8000-000000000002,00000002-0000-4000-8000-000000000003,00000002-0000-4000-8000-000000000004,00000002-0000-4000-8000-000000000005,00000002-0000-4000-8000-000000000006,00000002-0000-4000-8000-000000000007,00000002-0000-4000-8000-000000000008,00000002-0000-4000-8000-000000000009,00000002-0000-4000-8000-00000000000a,00000002-0000-4000-8000-00000000000b,00000002-0000-4000-8000-00000000000c,00000002-0000-4000-8000-00000000000d,00000002-0000-4000-8000-00000000000e,00000002-0000-4000-8000-00000000000f,00000002-0000-4000-8000-000000000010,00000002-0000-4000-8000-000000000011,00000002-0000-4000-8000-000000000012,00000002-0000-4000-8000-000000000013,00000002-0000-4000-8000-000000000014,00000002-0000-4000-8000-000000000015,00000002-0000-4000-8000-000000000016,00000002-0000-4000-8000-000000000017,00000002-0000-4000-8000-000000000018,00000002-0000-4000-8000-000000000019,00000002-0000-4000-8000-00000000001a,00000002-0000-4000-8000-00000000001b,00000002-0000-4000-8000-00000000001c,00000002-0000-4000-8000-00000000001d,00000002-0000-4000-8000-00000000001e,00000002-0000-4000-8000-00000000001f,00000002-0000-4000-8000-000000000020,00000002-0000-4000-8000-000000000021,00000002-0000-4000-8000-000000000022,00000002-0000-4000-8000-000000000023,00000002-0000-4000-8000-000000000024,00000002-0000-4000-8000-000000000025,00000002-0000-4000-8000-000000000026,00000002-0000-4000-8000-000000000027,00000002-0000-4000-8000-000000000028,00000002-0000-4000-8000-000000000029,00000002-0000-4000-8000-00000000002a,00000002-0000-4000-8000-00000000002b,00000002-0000-4000-8000-00000000002c,00000002-0000-4000-8000-00000000002d,00000002-0000-4000-8000-00000000002e,00000002-0000-4000-8000-00000000002f,00000002-0000-4000-8000-000000000030,00000002-0000-4000-8000-000000000031,00000002-0000-4000-8000-000000000032}'::uuid[]))
        Buffers: shared hit=150
Planning Time: 0.036 ms
Execution Time: 0.050 ms
```

</details>
