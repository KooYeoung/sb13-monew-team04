# 최근 조회 기사

> [MID4-132 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 이 조회는 사용자가 최근에 본 기사 10건과 기사별 댓글 수·조회 수를 가져온다.
- 가장 큰 테스트 데이터에서 실행 시간의 가운데 값이 `1,825.932 ms`로 네 조회 중 가장 느렸다.
- 사용자 조회 기록을 찾을 때 넓은 범위를 읽었고, 기사 10건의 댓글 수를 세기 위해 댓글 테이블도 반복해서 읽었다.
- 사용자별 최근 조회 인덱스와 기사별 댓글 인덱스를 함께 추가해야 하는 가장 우선적인 개선 대상으로 판단했다.

아래부터는 이 판단을 뒷받침하는 SQL과 실행계획을 기록한다.

이 문서의 `100k`, `1m`, `10m`은 각 테이블 row 수가 아니라 `seed_activity_history(scale_count)`에 전달한 seed scale이다. 실제 테이블별 row count는 README의 Seed 결과 표를 기준으로 본다.

최근 조회 기사는 `article_views.user_id` 조건으로 대상 사용자의 조회 기록을 찾은 뒤 `viewed_at DESC, id DESC`로 정렬한다. 현재 `uk_article_views_article_user(article_id, user_id)`와 `idx_article_views_article_viewed(article_id, viewed_at DESC)`는 모두 선두 컬럼이 `article_id`라 main query의 `user_id = ...` 조건에는 맞지 않는다. baseline 측정에서는 `V202608190001`에서 `idx_article_views_user_viewed`를 제거한 상태이므로 `article_views` main query가 `Seq Scan` 또는 `Parallel Seq Scan`으로 처리됐다. main query 정렬 비용은 `idx_article_views_article_viewed` 때문에 발생한 것이 아니라 `user_id` 선두 정렬 인덱스가 없는 것이 원인이다.

최근 조회 기사 댓글 수 subquery는 `comments.article_id` FK 무결성은 보장되지만 PostgreSQL에서 FK 컬럼 인덱스가 자동 생성되지 않아 `comments`를 반복 스캔한다. 반면 조회수 subquery는 `article_id = ...` 조건으로 기존 article_id 선두 인덱스를 사용하고 있어 1차 신규 후보로 보지 않는다. 100k seed scale에서는 `idx_article_views_article_viewed(article_id, viewed_at DESC)`를 사용한 뒤 `av2_0.user_id` sort가 추가됐고, 1m/10m seed scale에서는 `uk_article_views_article_user(article_id, user_id)`를 사용했다. `deleted_at IS NULL` filter는 PK index scan 이후 짧은 시간으로 처리되는 구간이 대부분이라 현재 실행계획에서는 1차 병목으로 보지 않는다.

## 다음에 확인할 인덱스

- 후보 A: `article_views(user_id, viewed_at DESC, id DESC)` 복합 인덱스를 적용해 `Seq Scan` 또는 `Parallel Seq Scan`과 sort 비용 제거 여부를 확인한다.
- 기대 효과: 대상 사용자 조회 기록을 먼저 좁힌 뒤 최신순 정렬을 인덱스 순서로 처리한다.
- 후보 B: `comments(article_id)` FK 조회 인덱스를 적용해 댓글 수 subquery의 `comments` 반복 full scan 제거 여부를 확인한다.
- 조회수 subquery는 seed scale에 따라 `idx_article_views_article_viewed(article_id, viewed_at DESC)` 또는 `uk_article_views_article_user(article_id, user_id)`를 `article_id = ...` 조건으로 사용하므로 신규 후보에서 제외한다.
- `idx_article_views_article_viewed`는 main query 정렬 개선에는 기여하지 못하므로, 후속 인덱스 적용 및 재측정 이후 실제 사용처가 없다면 제거 후보로 본다.
- `deleted_at IS NULL` 최적화는 후보 A, B 적용 후에도 조인 대상 filter 비용이 남는 경우 추가 측정한다.

## 실제로 실행한 SQL

### 최근 조회 기사

`MID4-95`의 `ArticleViewRepository.findRecentArticleViewActivities()` JPQL을 임시 Spring Boot test에서 실행하고 Hibernate SQL 로그로 캡처한 실제 query다. 아래 SQL은 측정을 위해 `userId` binding만 literal로 치환했다.

```sql
SELECT
    av1_0.id,
    u1_0.id,
    av1_0.viewed_at,
    a1_0.id,
    a1_0.source,
    a1_0.link,
    a1_0.title,
    a1_0.date,
    a1_0.summary,
    (
        SELECT COUNT(c1_0.id)
        FROM comments c1_0
        JOIN users u2_0 ON u2_0.id = c1_0.user_id
        WHERE c1_0.article_id = a1_0.id
          AND c1_0.deleted_at IS NULL
          AND u2_0.deleted_at IS NULL
    ),
    (
        SELECT COUNT(av2_0.id)
        FROM article_views av2_0
        JOIN users u3_0 ON u3_0.id = av2_0.user_id
        WHERE av2_0.article_id = a1_0.id
          AND u3_0.deleted_at IS NULL
    )
FROM article_views av1_0
JOIN articles a1_0 ON a1_0.id = av1_0.article_id
JOIN users u1_0 ON u1_0.id = av1_0.user_id
WHERE u1_0.id = '00000001-0000-4000-8000-000000000001'
  AND a1_0.deleted_at IS NULL
  AND u1_0.deleted_at IS NULL
ORDER BY av1_0.viewed_at DESC, av1_0.id DESC
FETCH FIRST 10 ROWS ONLY;
```

## 데이터 크기별 실행 시간

| Seed scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 최근 조회 기사 | `25.568 ms` | `25.981`, `27.132`, `27.095`, `27.150`, `26.654 ms` | `27.095 ms` |
| `1m` | 최근 조회 기사 | `235.410 ms` | `236.583`, `226.549`, `231.003`, `234.893`, `226.372 ms` | `231.003 ms` |
| `10m` | 최근 조회 기사 | `1820.813 ms` | `1825.932`, `1805.697`, `1861.183`, `1813.368`, `1832.137 ms` | `1825.932 ms` |

## DB가 데이터를 찾은 방법

### 100k

최근 조회 기사:

- `article_views`는 `user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `29000`
- 댓글 수 subquery는 `comments`를 10회 `Seq Scan`했다.
- subquery의 `Rows Removed by Filter`: `39998`, `loops=10`
- 조회수 subquery는 `idx_article_views_article_viewed`를 사용했고, `av2_0.user_id` 기준 sort가 추가됐다.

### 1m

최근 조회 기사:

- `article_views`는 `Parallel Seq Scan`으로 처리됐다.
- worker당 `Rows Removed by Filter`: `96667`, `loops=3`
- 댓글 수 subquery는 `comments`를 10회 `Seq Scan`했다.
- subquery의 `Rows Removed by Filter`: `399998`, `loops=10`
- 조회수 subquery는 `uk_article_views_article_user`를 사용했다.

### 10m

최근 조회 기사:

- `article_views`는 `Parallel Seq Scan`으로 처리됐다.
- worker당 `Rows Removed by Filter`: `996667`, `loops=3`
- 댓글 수 subquery는 `comments`를 10회 `Seq Scan`했다.
- subquery의 `Rows Removed by Filter`: `3999998`, `loops=10`
- 조회수 subquery는 `uk_article_views_article_user`를 사용했다.
- JIT total: `250.184 ms`

## 실행계획 원문

<details>
<summary>100k - 최근 조회 기사</summary>

```text
QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1531.32..12622.64 rows=10 width=200) (actual time=9.418..25.453 rows=10 loops=1)
   Buffers: shared hit=6799
   ->  Result  (cost=1531.32..1089590.40 rows=981 width=200) (actual time=9.416..25.448 rows=10 loops=1)
         Buffers: shared hit=6799
         ->  Sort  (cost=1531.32..1533.77 rows=981 width=184) (actual time=6.457..6.463 rows=10 loops=1)
               Sort Key: av1_0.viewed_at DESC, av1_0.id DESC
               Sort Method: top-N heapsort  Memory: 27kB
               Buffers: shared hit=859
               ->  Nested Loop  (cost=728.77..1510.12 rows=981 width=184) (actual time=1.704..6.319 rows=1000 loops=1)
                     Buffers: shared hit=856
                     ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.004..0.007 rows=1 loops=1)
                           Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=3
                     ->  Hash Join  (cost=728.50..1492.01 rows=981 width=184) (actual time=1.698..6.251 rows=1000 loops=1)
                           Hash Cond: (a1_0.id = av1_0.article_id)
                           Buffers: shared hit=853
                           ->  Seq Scan on articles a1_0  (cost=0.00..712.00 rows=19620 width=144) (actual time=0.002..3.428 rows=19620 loops=1)
                                 Filter: (deleted_at IS NULL)
                                 Rows Removed by Filter: 380
                                 Buffers: shared hit=512
                           ->  Hash  (cost=716.00..716.00 rows=1000 width=56) (actual time=1.684..1.685 rows=1000 loops=1)
                                 Buckets: 1024  Batches: 1  Memory Usage: 94kB
                                 Buffers: shared hit=341
                                 ->  Seq Scan on article_views av1_0  (cost=0.00..716.00 rows=1000 width=56) (actual time=0.002..1.536 rows=1000 loops=1)
                                       Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                                       Rows Removed by Filter: 29000
                                       Buffers: shared hit=341
         SubPlan 1
           ->  Aggregate  (cost=1096.61..1096.62 rows=1 width=8) (actual time=1.873..1.873 rows=1 loops=10)
                 Buffers: shared hit=5860
                 ->  Nested Loop  (cost=0.28..1096.60 rows=2 width=16) (actual time=0.005..1.871 rows=2 loops=10)
                       Buffers: shared hit=5860
                       ->  Seq Scan on comments c1_0  (cost=0.00..1080.00 rows=2 width=32) (actual time=0.003..1.864 rows=2 loops=10)
                             Filter: ((deleted_at IS NULL) AND (article_id = a1_0.id))
                             Rows Removed by Filter: 39998
                             Buffers: shared hit=5800
                       ->  Index Scan using pk_users on users u2_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=20)
                             Index Cond: (id = c1_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=60
         SubPlan 2
           ->  Aggregate  (cost=12.49..12.50 rows=1 width=8) (actual time=0.022..0.023 rows=1 loops=10)
                 Buffers: shared hit=80
                 ->  Merge Join  (cost=12.15..12.49 rows=2 width=16) (actual time=0.019..0.021 rows=3 loops=10)
                       Merge Cond: (u3_0.id = av2_0.user_id)
                       Buffers: shared hit=80
                       ->  Index Scan using pk_users on users u3_0  (cost=0.28..58.27 rows=990 width=16) (actual time=0.003..0.004 rows=4 loops=10)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
                       ->  Sort  (cost=11.88..11.88 rows=2 width=32) (actual time=0.014..0.014 rows=3 loops=10)
                             Sort Key: av2_0.user_id
                             Sort Method: quicksort  Memory: 25kB
                             Buffers: shared hit=50
                             ->  Bitmap Heap Scan on article_views av2_0  (cost=4.30..11.87 rows=2 width=32) (actual time=0.006..0.008 rows=3 loops=10)
                                   Recheck Cond: (article_id = a1_0.id)
                                   Heap Blocks: exact=30
                                   Buffers: shared hit=50
                                   ->  Bitmap Index Scan on idx_article_views_article_viewed  (cost=0.00..4.30 rows=2 width=0) (actual time=0.004..0.004 rows=3 loops=10)
                                         Index Cond: (article_id = a1_0.id)
                                         Buffers: shared hit=20
 Planning:
   Buffers: shared hit=363
 Planning Time: 0.990 ms
 Execution Time: 25.568 ms
(65 rows)
```

</details>

<details>
<summary>1m - 최근 조회 기사</summary>

```text
QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=6229.36..114556.42 rows=10 width=203) (actual time=48.550..222.011 rows=10 loops=1)
   Buffers: shared hit=15961 read=45710
   ->  Nested Loop  (cost=6229.36..108441611.67 rows=10010 width=203) (actual time=38.800..212.250 rows=10 loops=1)
         Buffers: shared hit=15961 read=45710
         ->  Nested Loop  (cost=6229.08..35427.11 rows=10010 width=187) (actual time=17.725..17.967 rows=10 loops=1)
               Buffers: shared hit=3538
               ->  Gather Merge  (cost=6228.66..7416.61 rows=10200 width=56) (actual time=17.663..17.815 rows=10 loops=1)
                     Workers Planned: 2
                     Workers Launched: 2
                     Buffers: shared hit=3498
                     ->  Sort  (cost=5228.63..5239.26 rows=4250 width=56) (actual time=7.319..7.325 rows=3 loops=3)
                           Sort Key: av1_0.viewed_at DESC, av1_0.id DESC
                           Sort Method: quicksort  Memory: 1166kB
                           Buffers: shared hit=3498
                           Worker 0:  Sort Method: quicksort  Memory: 25kB
                           Worker 1:  Sort Method: quicksort  Memory: 25kB
                           ->  Parallel Seq Scan on article_views av1_0  (cost=0.00..4972.50 rows=4250 width=56) (actual time=1.732..6.884 rows=3333 loops=3)
                                 Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Rows Removed by Filter: 96667
                                 Buffers: shared hit=3410
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.42..2.75 rows=1 width=147) (actual time=0.009..0.009 rows=1 loops=10)
                     Index Cond: (id = av1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.29..8.31 rows=1 width=16) (actual time=0.003..0.004 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.012..0.013 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=10814.61..10814.62 rows=1 width=8) (actual time=19.381..19.381 rows=1 loops=10)
                 Buffers: shared hit=12330 read=45710
                 ->  Nested Loop  (cost=0.29..10814.61 rows=2 width=16) (actual time=0.012..19.372 rows=2 loops=10)
                       Buffers: shared hit=12330 read=45710
                       ->  Seq Scan on comments c1_0  (cost=0.00..10798.00 rows=2 width=32) (actual time=0.004..19.350 rows=2 loops=10)
                             Filter: ((deleted_at IS NULL) AND (article_id = a1_0.id))
                             Rows Removed by Filter: 399998
                             Buffers: shared hit=12270 read=45710
                       ->  Index Scan using pk_users on users u2_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.005..0.005 rows=1 loops=20)
                             Index Cond: (id = c1_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=60
         SubPlan 2
           ->  Aggregate  (cost=15.14..15.15 rows=1 width=8) (actual time=0.030..0.030 rows=1 loops=10)
                 Buffers: shared hit=90
                 ->  Merge Join  (cost=0.71..15.14 rows=2 width=16) (actual time=0.021..0.025 rows=3 loops=10)
                       Merge Cond: (av2_0.user_id = u3_0.id)
                       Buffers: shared hit=90
                       ->  Index Scan using uk_article_views_article_user on article_views av2_0  (cost=0.42..12.32 rows=2 width=32) (actual time=0.011..0.014 rows=3 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Buffers: shared hit=60
                       ->  Index Scan using pk_users on users u3_0  (cost=0.29..471.29 rows=9900 width=16) (actual time=0.004..0.005 rows=3 loops=10)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
 Planning:
   Buffers: shared hit=367
 Planning Time: 0.904 ms
 JIT:
   Functions: 54
   Options: Inlining false, Optimization false, Expressions true, Deforming true
   Timing: Generation 1.344 ms, Inlining 0.000 ms, Optimization 0.964 ms, Emission 14.000 ms, Total 16.308 ms
 Execution Time: 235.410 ms
(63 rows)
```

</details>

<details>
<summary>10m - 최근 조회 기사</summary>

```text
QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=50984.29..1158763.04 rows=10 width=206) (actual time=391.618..1807.593 rows=10 loops=1)
   Buffers: shared hit=16364 read=625351
   ->  Nested Loop  (cost=50984.29..1150700779.48 rows=10387 width=206) (actual time=229.989..1645.952 rows=10 loops=1)
         Buffers: shared hit=16364 read=625351
         ->  Nested Loop  (cost=50983.87..120530.99 rows=10387 width=190) (actual time=72.973..79.128 rows=10 loops=1)
               Buffers: shared hit=4948 read=29271
               ->  Gather Merge  (cost=50983.44..52217.99 rows=10600 width=56) (actual time=72.914..78.972 rows=10 loops=1)
                     Workers Planned: 2
                     Workers Launched: 2
                     Buffers: shared hit=4908 read=29271
                     ->  Sort  (cost=49983.42..49994.46 rows=4417 width=56) (actual time=62.421..62.438 rows=546 loops=3)
                           Sort Key: av1_0.viewed_at DESC, av1_0.id DESC
                           Sort Method: quicksort  Memory: 317kB
                           Buffers: shared hit=4908 read=29271
                           Worker 0:  Sort Method: quicksort  Memory: 351kB
                           Worker 1:  Sort Method: quicksort  Memory: 403kB
                           ->  Parallel Seq Scan on article_views av1_0  (cost=0.00..49715.99 rows=4417 width=56) (actual time=52.858..62.101 rows=3333 loops=3)
                                 Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Rows Removed by Filter: 996667
                                 Buffers: shared hit=4820 read=29271
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.43..6.44 rows=1 width=150) (actual time=0.009..0.009 rows=1 loops=10)
                     Index Cond: (id = av1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.003..0.004 rows=1 loops=10)
               Buffers: shared hit=4
               ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.022..0.023 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
         SubPlan 1
           ->  Aggregate  (cost=110746.06..110746.07 rows=1 width=8) (actual time=156.624..156.624 rows=1 loops=10)
                 Buffers: shared hit=11340 read=596080
                 ->  Nested Loop  (cost=0.42..110746.06 rows=2 width=16) (actual time=0.015..156.613 rows=2 loops=10)
                       Buffers: shared hit=11340 read=596080
                       ->  Seq Scan on comments c1_0  (cost=0.00..110729.19 rows=2 width=32) (actual time=0.003..156.570 rows=2 loops=10)
                             Filter: ((deleted_at IS NULL) AND (article_id = a1_0.id))
                             Rows Removed by Filter: 3999998
                             Buffers: shared hit=11260 read=596080
                       ->  Index Scan using pk_users on users u2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.011..0.011 rows=1 loops=20)
                             Index Cond: (id = c1_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=80
         SubPlan 2
           ->  Aggregate  (cost=25.08..25.09 rows=1 width=8) (actual time=0.039..0.039 rows=1 loops=10)
                 Buffers: shared hit=72
                 ->  Nested Loop  (cost=0.86..25.08 rows=2 width=16) (actual time=0.031..0.036 rows=3 loops=10)
                       Buffers: shared hit=72
                       ->  Index Scan using uk_article_views_article_user on article_views av2_0  (cost=0.43..12.38 rows=2 width=32) (actual time=0.016..0.018 rows=3 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Buffers: shared hit=60
                       ->  Memoize  (cost=0.43..8.45 rows=1 width=16) (actual time=0.004..0.004 rows=1 loops=30)
                             Cache Key: av2_0.user_id
                             Cache Mode: logical
                             Hits: 27  Misses: 3  Evictions: 0  Overflows: 0  Memory Usage: 1kB
                             Buffers: shared hit=12
                             ->  Index Scan using pk_users on users u3_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.005..0.005 rows=1 loops=3)
                                   Index Cond: (id = av2_0.user_id)
                                   Filter: (deleted_at IS NULL)
                                   Buffers: shared hit=12
 Planning:
   Buffers: shared hit=371
 Planning Time: 0.896 ms
 JIT:
   Functions: 59
   Options: Inlining true, Optimization true, Expressions true, Deforming true
   Timing: Generation 1.223 ms, Inlining 92.071 ms, Optimization 87.237 ms, Emission 69.653 ms, Total 250.184 ms
 Execution Time: 1820.813 ms
(68 rows)
```

</details>
