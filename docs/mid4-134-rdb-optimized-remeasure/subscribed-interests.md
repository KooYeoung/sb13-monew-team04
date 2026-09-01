# 구독 중인 관심사 재측정

> [MID4-134 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 사용자별 최근 구독 인덱스를 적용하자 가장 큰 테스트 데이터에서 두 SQL의 실행 시간 가운데 값 합계가 `11.635 ms`에서 `1.283 ms`로 88.97% 줄었다.
- DB가 전체 구독을 넓게 읽는 대신 대상 사용자의 구독 50건부터 찾게 됐다.
- 이 측정에서는 사용자 구독 수와 관심사별 구독자 수가 고정돼 있어 한 관심사에 구독이 몰리는 상황까지 확인한 것은 아니다.

아래부터는 이 결론의 측정값과 실행계획을 기록한다.

100k scale에서는 interests가 500건이고 target user 기준 keywords 결과가 150건이라 PostgreSQL optimizer가 일부 작은 테이블에 대해 Seq Scan을 선택했다. 이는 인덱스가 적용되지 않은 장애라기보다 작은 데이터셋에서 전체 scan 비용이 더 낮다고 판단한 실행계획에 가깝다.

사용자 구독 row를 찾는 핵심 접근 경로는 100k부터 idx_subscriptions_user_created_id를 사용한다. 1m/10m scale에서는 keywords 조회도 uk_keywords_interest_keyword를 사용하며, 10m main query는 idx_subscriptions_user_created_id Index Scan 순서로 정렬 비용 없이 처리된다.

다만 이번 재측정은 seed scale 증가에 따른 전체 테이블 크기 변화와 인덱스 접근 경로를 확인한 것이며, target user의 구독 수 50건과 keywords 결과 150건은 scale별로 동일하다. 따라서 사용자별 구독 수나 관심사별 구독자 수 fan-out이 크게 증가하는 worst-case까지 검증한 결과는 아니다. 현재 측정값에서는 병목으로 보기 어려우므로 MID4-134에서는 쿼리 구조 변경 없이 인덱스 적용 효과와 측정 한계를 기록하고, fan-out 증가 시에는 `interestSubscriberCount` 계산 방식 개선을 후속 후보로 둔다.

## 어떤 인덱스를 적용했나

| index | columns | 목적 |
| --- | --- | --- |
| idx_subscriptions_user_created_id | subscriptions(user_id, created_at DESC, id DESC) | 사용자 필터와 최신순 정렬 |

keywords 조회는 100k scale에서는 Seq Scan을 선택했고, 1m/10m scale에서는 기존 uk_keywords_interest_keyword 인덱스를 사용한다.

## 측정 기준

- raw: [raw/rerun-132-method](raw/rerun-132-method)
- SQL template: [raw/rerun-132-method/sql-template.sql](raw/rerun-132-method/sql-template.sql)
- 측정 방식: seed 후 ANALYZE, 조회별 3회 warm-up, EXPLAIN (ANALYZE, BUFFERS), 동일 SQL 5회 반복 실행 후 median 기록
- 요청 1건 기준 구독 관심사는 main query와 keywords batch query를 합산해 total을 산정

## 실행 시간

### 구독 목록 조회(main query)

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `5.517` ms | `5.776` ms | `3.951`, `3.803`, `4.980`, `4.414`, `4.327` ms | `4.318`, `3.504`, `3.875`, `3.432`, `3.474` ms | `4.327` ms | `3.504` ms |
| `1m` | `2.666` ms | `1.187` ms | `2.961`, `2.884`, `2.772`, `2.762`, `2.634` ms | `1.250`, `1.483`, `1.144`, `1.199`, `1.163` ms | `2.772` ms | `1.199` ms |
| `10m` | `10.052` ms | `0.650` ms | `11.051`, `10.541`, `10.535`, `10.878`, `10.810` ms | `0.981`, `0.923`, `1.016`, `0.922`, `0.895` ms | `10.810` ms | `0.923` ms |

### 키워드 조회(keywords query)

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `0.099` ms | `0.252` ms | `0.538`, `0.449`, `0.446`, `0.450`, `0.517` ms | `0.472`, `0.490`, `0.456`, `0.621`, `0.519` ms | `0.450` ms | `0.490` ms |
| `1m` | `0.047` ms | `0.059` ms | `0.451`, `0.464`, `0.572`, `0.585`, `0.292` ms | `0.331`, `0.389`, `0.361`, `0.301`, `0.297` ms | `0.464` ms | `0.331` ms |
| `10m` | `0.050` ms | `0.065` ms | `0.517`, `0.391`, `0.496`, `0.596`, `0.486` ms | `0.373`, `0.305`, `0.267`, `0.294`, `0.499` ms | `0.496` ms | `0.305` ms |

### 두 SQL 실행 시간 가운데 값 합계

| seed scale | baseline total repeats | optimized total repeats | baseline median | optimized median | delta | change |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `100k` | `4.472`, `4.481`, `4.332`, `4.380`, `5.195` ms | `4.790`, `3.994`, `4.331`, `4.053`, `3.993` ms | `4.472` ms | `4.053` ms | `-0.419` ms | `-9.37`% |
| `1m` | `4.708`, `4.280`, `3.549`, `3.453`, `3.331` ms | `1.581`, `1.872`, `1.505`, `1.500`, `1.460` ms | `3.549` ms | `1.505` ms | `-2.044` ms | `-57.59`% |
| `10m` | `11.635`, `11.394`, `12.454`, `14.413`, `11.481` ms | `1.354`, `1.228`, `1.283`, `1.216`, `1.394` ms | `11.635` ms | `1.283` ms | `-10.352` ms | `-88.97`% |

## DB가 데이터를 찾은 방법

| seed scale | main query 주요 접근 경로 | keywords query 주요 접근 경로 |
| --- | --- | --- |
| 100k | idx_subscriptions_user_created_id Bitmap Index Scan, interests Seq Scan, uk_subscriptions_interest_user | keywords Seq Scan, idx_subscriptions_user_created_id Bitmap Index Scan |
| 1m | idx_subscriptions_user_created_id Bitmap Index Scan, interests Seq Scan, uk_subscriptions_interest_user | uk_keywords_interest_keyword, idx_subscriptions_user_created_id Bitmap Index Scan |
| 10m | idx_subscriptions_user_created_id Index Scan, pk_interests, uk_subscriptions_interest_user | uk_keywords_interest_keyword, idx_subscriptions_user_created_id Index Scan |

Execution Time:

| seed scale | main | keywords |
| --- | ---: | ---: |
| 100k | 5.776 ms | 0.252 ms |
| 1m | 1.187 ms | 0.059 ms |
| 10m | 0.650 ms | 0.065 ms |

## 실행계획 원문

<details>
<summary>100k - 구독 중인 관심사</summary>

main query:

```text
                                                                                   QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=3512.50..3512.62 rows=50 width=76) (actual time=5.746..5.749 rows=50 loops=1)
   Sort Key: s1_0.created_at DESC, s1_0.id DESC
   Sort Method: quicksort  Memory: 30kB
   Buffers: shared hit=1559
   ->  Nested Loop  (cost=60.50..3511.09 rows=50 width=76) (actual time=0.338..5.727 rows=50 loops=1)
         Buffers: shared hit=1559
         ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.003..0.003 rows=1 loops=1)
               Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: (deleted_at IS NULL)
               Buffers: shared hit=3
         ->  Hash Join  (cost=60.22..71.55 rows=50 width=84) (actual time=0.019..0.071 rows=50 loops=1)
               Hash Cond: (i1_0.id = s1_0.interest_id)
               Buffers: shared hit=8
               ->  Seq Scan on interests i1_0  (cost=0.00..10.00 rows=500 width=44) (actual time=0.001..0.021 rows=500 loops=1)
                     Buffers: shared hit=5
               ->  Hash  (cost=59.60..59.60 rows=50 width=56) (actual time=0.011..0.011 rows=50 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 13kB
                     Buffers: shared hit=3
                     ->  Bitmap Heap Scan on subscriptions s1_0  (cost=4.67..59.60 rows=50 width=56) (actual time=0.003..0.006 rows=50 loops=1)
                           Recheck Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Heap Blocks: exact=1
                           Buffers: shared hit=3
                           ->  Bitmap Index Scan on idx_subscriptions_user_created_id  (cost=0.00..4.66 rows=50 width=0) (actual time=0.002..0.002 rows=50 loops=1)
                                 Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Buffers: shared hit=2
         SubPlan 1
           ->  Aggregate  (cost=68.60..68.61 rows=1 width=8) (actual time=0.113..0.113 rows=1 loops=50)
                 Buffers: shared hit=1548
                 ->  Nested Loop  (cost=31.85..68.58 rows=10 width=16) (actual time=0.009..0.112 rows=11 loops=50)
                       Buffers: shared hit=1548
                       ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.27..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                             Index Cond: (id = i1_0.id)
                             Heap Fetches: 50
                             Buffers: shared hit=150
                       ->  Hash Join  (cost=31.58..60.19 rows=10 width=32) (actual time=0.007..0.109 rows=11 loops=50)
                             Hash Cond: (u2_0.id = s2_0.user_id)
                             Buffers: shared hit=1398
                             ->  Seq Scan on users u2_0  (cost=0.00..26.00 rows=990 width=16) (actual time=0.001..0.061 rows=990 loops=50)
                                   Filter: (deleted_at IS NULL)
                                   Rows Removed by Filter: 10
                                   Buffers: shared hit=800
                             ->  Hash  (cost=31.45..31.45 rows=10 width=48) (actual time=0.006..0.006 rows=11 loops=50)
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
   Buffers: shared hit=24
 Planning Time: 0.189 ms
 Execution Time: 5.776 ms
(55 rows)
```

keywords query:

```text
                                                                     QUERY PLAN
----------------------------------------------------------------------------------------------------------------------------------------------------
 Seq Scan on keywords k1_0  (cost=59.60..108.35 rows=30 width=45) (actual time=0.013..0.239 rows=150 loops=1)
   Filter: (interest_id = ANY ($0))
   Rows Removed by Filter: 1350
   Buffers: shared hit=18
   InitPlan 1 (returns $0)
     ->  Bitmap Heap Scan on subscriptions s1_0  (cost=4.67..59.60 rows=50 width=16) (actual time=0.005..0.007 rows=50 loops=1)
           Recheck Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
           Heap Blocks: exact=1
           Buffers: shared hit=3
           ->  Bitmap Index Scan on idx_subscriptions_user_created_id  (cost=0.00..4.66 rows=50 width=0) (actual time=0.003..0.003 rows=50 loops=1)
                 Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                 Buffers: shared hit=2
 Planning Time: 0.046 ms
 Execution Time: 0.252 ms
(14 rows)
```

</details>
<details>
<summary>1m - 구독 중인 관심사</summary>

main query:

```text
                                                                                QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=6930.81..6930.94 rows=52 width=77) (actual time=1.161..1.163 rows=50 loops=1)
   Sort Key: s1_0.created_at DESC, s1_0.id DESC
   Sort Method: quicksort  Memory: 30kB
   Buffers: shared hit=2487
   ->  Nested Loop  (cost=159.71..6929.32 rows=52 width=77) (actual time=0.039..1.151 rows=50 loops=1)
         Buffers: shared hit=2487
         ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.002..0.003 rows=1 loops=1)
               Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: (deleted_at IS NULL)
               Buffers: shared hit=3
         ->  Hash Join  (cost=159.43..269.56 rows=52 width=85) (actual time=0.014..0.401 rows=50 loops=1)
               Hash Cond: (i1_0.id = s1_0.interest_id)
               Buffers: shared hit=51
               ->  Seq Scan on interests i1_0  (cost=0.00..97.00 rows=5000 width=45) (actual time=0.001..0.189 rows=5000 loops=1)
                     Buffers: shared hit=47
               ->  Hash  (cost=158.78..158.78 rows=52 width=56) (actual time=0.011..0.011 rows=50 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 13kB
                     Buffers: shared hit=4
                     ->  Bitmap Heap Scan on subscriptions s1_0  (cost=4.82..158.78 rows=52 width=56) (actual time=0.004..0.006 rows=50 loops=1)
                           Recheck Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Heap Blocks: exact=1
                           Buffers: shared hit=4
                           ->  Bitmap Index Scan on idx_subscriptions_user_created_id  (cost=0.00..4.80 rows=52 width=0) (actual time=0.002..0.002 rows=50 loops=1)
                                 Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=127.89..127.90 rows=1 width=8) (actual time=0.015..0.015 rows=1 loops=50)
                 Buffers: shared hit=2433
                 ->  Nested Loop  (cost=5.06..127.87 rows=10 width=16) (actual time=0.004..0.014 rows=11 loops=50)
                       Buffers: shared hit=2433
                       ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.28..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                             Index Cond: (id = i1_0.id)
                             Heap Fetches: 50
                             Buffers: shared hit=150
                       ->  Nested Loop  (cost=4.78..119.47 rows=10 width=32) (actual time=0.003..0.013 rows=11 loops=50)
                             Buffers: shared hit=2283
                             ->  Bitmap Heap Scan on subscriptions s2_0  (cost=4.49..40.44 rows=10 width=48) (actual time=0.002..0.004 rows=11 loops=50)
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
   Buffers: shared hit=28
 Planning Time: 0.165 ms
 Execution Time: 1.187 ms
(52 rows)
```

keywords query:

```text
                                                                     QUERY PLAN
----------------------------------------------------------------------------------------------------------------------------------------------------
 Bitmap Heap Scan on keywords k1_0  (cost=201.86..277.10 rows=30 width=46) (actual time=0.035..0.045 rows=150 loops=1)
   Recheck Cond: (interest_id = ANY ($0))
   Heap Blocks: exact=4
   Buffers: shared hit=108
   InitPlan 1 (returns $0)
     ->  Bitmap Heap Scan on subscriptions s1_0  (cost=4.82..158.78 rows=52 width=16) (actual time=0.006..0.009 rows=50 loops=1)
           Recheck Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
           Heap Blocks: exact=1
           Buffers: shared hit=4
           ->  Bitmap Index Scan on idx_subscriptions_user_created_id  (cost=0.00..4.80 rows=52 width=0) (actual time=0.005..0.005 rows=50 loops=1)
                 Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                 Buffers: shared hit=3
   ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..43.08 rows=30 width=0) (actual time=0.033..0.033 rows=150 loops=1)
         Index Cond: (interest_id = ANY ($0))
         Buffers: shared hit=104
 Planning Time: 0.047 ms
 Execution Time: 0.059 ms
(17 rows)
```

</details>
<details>
<summary>10m - 구독 중인 관심사</summary>

main query:

```text
                                                                                    QUERY PLAN
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Nested Loop  (cost=1.13..760.05 rows=5 width=78) (actual time=0.025..0.630 rows=50 loops=1)
   Buffers: shared hit=3182
   ->  Nested Loop  (cost=0.71..65.67 rows=5 width=86) (actual time=0.005..0.041 rows=50 loops=1)
         Buffers: shared hit=154
         ->  Index Scan using idx_subscriptions_user_created_id on subscriptions s1_0  (cost=0.42..24.13 rows=5 width=56) (actual time=0.003..0.007 rows=50 loops=1)
               Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
               Buffers: shared hit=4
         ->  Index Scan using pk_interests on interests i1_0  (cost=0.29..8.31 rows=1 width=46) (actual time=0.000..0.001 rows=1 loops=50)
               Index Cond: (id = s1_0.interest_id)
               Buffers: shared hit=150
   ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=50)
         Buffers: shared hit=4
         ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.002 rows=1 loops=1)
               Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: (deleted_at IS NULL)
               Buffers: shared hit=4
   SubPlan 1
     ->  Aggregate  (cost=137.17..137.18 rows=1 width=8) (actual time=0.011..0.011 rows=1 loops=50)
           Buffers: shared hit=3024
           ->  Nested Loop  (cost=1.13..137.14 rows=10 width=16) (actual time=0.002..0.011 rows=11 loops=50)
                 Buffers: shared hit=3024
                 ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.29..8.31 rows=1 width=16) (actual time=0.000..0.001 rows=1 loops=50)
                       Index Cond: (id = i1_0.id)
                       Heap Fetches: 50
                       Buffers: shared hit=150
                 ->  Nested Loop  (cost=0.84..128.73 rows=10 width=32) (actual time=0.001..0.010 rows=11 loops=50)
                       Buffers: shared hit=2874
                       ->  Index Scan using uk_subscriptions_interest_user on subscriptions s2_0  (cost=0.42..44.38 rows=10 width=48) (actual time=0.001..0.002 rows=11 loops=50)
                             Index Cond: (interest_id = i1_0.id)
                             Buffers: shared hit=694
                       ->  Index Scan using pk_users on users u2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=545)
                             Index Cond: (id = s2_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Rows Removed by Filter: 0
                             Buffers: shared hit=2180
 Planning:
   Buffers: shared hit=30
 Planning Time: 0.161 ms
 Execution Time: 0.650 ms
(39 rows)
```

keywords query:

```text
                                                                           QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------
 Bitmap Heap Scan on keywords k1_0  (cost=68.57..176.08 rows=30 width=47) (actual time=0.043..0.052 rows=150 loops=1)
   Recheck Cond: (interest_id = ANY ($0))
   Heap Blocks: exact=4
   Buffers: shared hit=158
   InitPlan 1 (returns $0)
     ->  Index Scan using idx_subscriptions_user_created_id on subscriptions s1_0  (cost=0.42..24.13 rows=5 width=16) (actual time=0.005..0.009 rows=50 loops=1)
           Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
           Buffers: shared hit=4
   ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..44.43 rows=30 width=0) (actual time=0.040..0.040 rows=150 loops=1)
         Index Cond: (interest_id = ANY ($0))
         Buffers: shared hit=154
 Planning Time: 0.047 ms
 Execution Time: 0.065 ms
(13 rows)
```

</details>
