# 최근 좋아요한 댓글

> [MID4-132 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 이 조회는 사용자가 최근에 좋아요를 누른 댓글 10건과 각 댓글의 좋아요 수를 가져온다.
- 가장 큰 테스트 데이터에서 실행 시간의 가운데 값은 `45.905 ms`였다.
- 기존 인덱스는 댓글별 좋아요를 찾는 순서여서 특정 사용자가 누른 좋아요를 바로 찾는 데 맞지 않았다.
- 좋아요를 누른 사용자와 최신순을 함께 처리하는 인덱스를 다음 개선 후보로 정했다.

아래부터는 이 판단을 뒷받침하는 SQL과 실행계획을 기록한다.

이 문서의 `100k`, `1m`, `10m`은 각 테이블 row 수가 아니라 `seed_activity_history(scale_count)`에 전달한 seed scale이다. 실제 테이블별 row count는 README의 Seed 결과 표를 기준으로 본다.

최근 좋아요한 댓글은 `comment_likes.liked_by` 조건으로 대상 사용자의 좋아요를 찾은 뒤 `created_at DESC, id DESC`로 정렬한다. 현재 `uk_comment_likes_comment_liked_by(comment_id, liked_by)`는 좋아요 수 subquery의 `comment_id = ...` 조건에는 사용되지만, main query의 `liked_by = ...` 조건에는 복합 인덱스의 선두 컬럼인 `comment_id`가 고정되지 않아 효과적으로 사용되기 어렵다. `deleted_at IS NULL`은 `comment_likes` 자체가 아니라 조인 대상인 `users`, `comments`, `articles` filter에서 발생하며, 현재 실행계획에서는 main table 접근 경로와 정렬 비용이 더 큰 병목으로 보인다.

## 다음에 확인할 인덱스

- 후보: `comment_likes(liked_by, created_at DESC, id DESC)` 복합 인덱스를 적용해 `Seq Scan` 또는 `Parallel Seq Scan`과 sort 비용 제거 여부를 확인한다.
- 기대 효과: 대상 사용자가 좋아요한 row를 먼저 좁힌 뒤 최신순 정렬을 인덱스 순서로 처리한다.
- `comment_id IS NOT NULL` 조건을 추가해 기존 `(comment_id, liked_by)` unique index 사용을 유도할 여지는 있지만, `comment_id`는 `NOT NULL` FK 컬럼이라 선택도가 거의 없다. 따라서 인덱스를 타더라도 넓은 인덱스 범위를 스캔한 뒤 `liked_by`를 filter할 가능성이 높고, 기존 인덱스에는 `created_at DESC, id DESC`가 없어 최신순 정렬 비용도 해결하지 못한다.
- `100k` seed scale에서는 전체 row 수가 작고 main query에 적합한 인덱스가 없어 planner가 `Seq Scan`을 선택한 것으로 보며, 후보 인덱스 적용 후 seed scale별 실행계획 변화를 비교한다.
- `deleted_at IS NULL` 최적화는 복합 인덱스 적용 후에도 조인 대상 filter 비용이 남는 경우 추가 측정한다.

## 실제로 실행한 SQL

### 최근 좋아요한 댓글

`MID4-94`의 `CommentLikeRepository.findRecentCommentLikeActivity()` JPQL을 임시 Spring Boot test에서 실행하고 Hibernate SQL 로그로 캡처한 실제 query다. 아래 SQL은 측정을 위해 `userId` binding만 literal로 치환했다.

```sql
SELECT
    cl1_0.id,
    cl1_0.created_at,
    c1_0.id,
    a1_0.id,
    a1_0.title,
    u1_0.id,
    u1_0.nickname,
    c1_0.content,
    (
        SELECT COUNT(cl2_0.id)
        FROM comment_likes cl2_0
        JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id
        JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by
        WHERE c2_0.id = c1_0.id
          AND lb2_0.deleted_at IS NULL
    ),
    c1_0.created_at
FROM comment_likes cl1_0
JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id
JOIN users u1_0 ON u1_0.id = c1_0.user_id
JOIN articles a1_0 ON a1_0.id = c1_0.article_id
JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by
WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001'
  AND lb1_0.deleted_at IS NULL
  AND u1_0.deleted_at IS NULL
  AND a1_0.deleted_at IS NULL
  AND c1_0.deleted_at IS NULL
ORDER BY cl1_0.created_at DESC, cl1_0.id DESC
FETCH FIRST 10 ROWS ONLY;
```

## 데이터 크기별 실행 시간

| Seed scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 최근 좋아요한 댓글 | `8.936 ms` | `9.066`, `9.725`, `11.235`, `9.524`, `8.936 ms` | `9.524 ms` |
| `1m` | 최근 좋아요한 댓글 | `10.515 ms` | `12.238`, `11.750`, `12.216`, `11.807`, `11.632 ms` | `11.807 ms` |
| `10m` | 최근 좋아요한 댓글 | `44.102 ms` | `48.387`, `52.918`, `45.801`, `44.662`, `45.905 ms` | `45.905 ms` |

## DB가 데이터를 찾은 방법

### 100k

최근 좋아요한 댓글:

- main query의 `comment_likes`는 `liked_by` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `29000`
- `(comment_id, liked_by)` 인덱스는 `liked_by` 단독 조건에는 사용할 수 없어 main query에는 적용되지 않았다.
- 좋아요 수 subquery는 `uk_comment_likes_comment_liked_by` 인덱스를 사용했다.

### 1m

최근 좋아요한 댓글:

- main query의 `comment_likes`는 `Parallel Seq Scan`으로 처리됐다.
- worker당 `Rows Removed by Filter`: `96667`, `loops=3`
- 좋아요 수 subquery는 `uk_comment_likes_comment_liked_by` 인덱스를 사용했다.

### 10m

최근 좋아요한 댓글:

- main query의 `comment_likes`는 `Parallel Seq Scan`으로 처리됐다.
- worker당 `Rows Removed by Filter`: `996667`, `loops=3`
- 좋아요 수 subquery는 `uk_comment_likes_comment_liked_by` 인덱스를 사용했다.

## 실행계획 원문

<details>
<summary>100k - 최근 좋아요한 댓글</summary>

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=2202.09..2451.66 rows=10 width=156) (actual time=8.795..8.845 rows=10 loops=1)
   Buffers: shared hit=4045
   ->  Result  (cost=2202.09..25286.56 rows=925 width=156) (actual time=8.793..8.842 rows=10 loops=1)
         Buffers: shared hit=4045
         ->  Sort  (cost=2202.09..2204.41 rows=925 width=148) (actual time=8.742..8.746 rows=10 loops=1)
               Sort Key: cl1_0.created_at DESC, cl1_0.id DESC
               Sort Method: top-N heapsort  Memory: 27kB
               Buffers: shared hit=3885
               ->  Nested Loop  (cost=736.44..2182.10 rows=925 width=148) (actual time=2.204..8.593 rows=970 loops=1)
                     Buffers: shared hit=3879
                     ->  Index Scan using pk_users on users lb1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.005..0.007 rows=1 loops=1)
                           Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=3
                     ->  Nested Loop  (cost=736.16..2164.56 rows=925 width=164) (actual time=2.198..8.526 rows=970 loops=1)
                           Buffers: shared hit=3876
                           ->  Hash Join  (cost=735.88..1818.47 rows=943 width=139) (actual time=2.180..7.650 rows=990 loops=1)
                                 Hash Cond: (c1_0.user_id = u1_0.id)
                                 Buffers: shared hit=906
                                 ->  Hash Join  (cost=697.50..1777.58 rows=953 width=126) (actual time=1.959..7.289 rows=1000 loops=1)
                                       Hash Cond: (c1_0.id = cl1_0.comment_id)
                                       Buffers: shared hit=890
                                       ->  Seq Scan on comments c1_0  (cost=0.00..980.00 rows=38123 width=86) (actual time=0.003..3.452 rows=38100 loops=1)
                                             Filter: (deleted_at IS NULL)
                                             Rows Removed by Filter: 1900
                                             Buffers: shared hit=580
                                       ->  Hash  (cost=685.00..685.00 rows=1000 width=56) (actual time=1.790..1.791 rows=1000 loops=1)
                                             Buckets: 1024  Batches: 1  Memory Usage: 94kB
                                             Buffers: shared hit=310
                                             ->  Seq Scan on comment_likes cl1_0  (cost=0.00..685.00 rows=1000 width=56) (actual time=0.004..1.699 rows=1000 loops=1)
                                                   Filter: (liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                                   Rows Removed by Filter: 29000
                                                   Buffers: shared hit=310
                                 ->  Hash  (cost=26.00..26.00 rows=990 width=29) (actual time=0.213..0.214 rows=990 loops=1)
                                       Buckets: 1024  Batches: 1  Memory Usage: 68kB
                                       Buffers: shared hit=16
                                       ->  Seq Scan on users u1_0  (cost=0.00..26.00 rows=990 width=29) (actual time=0.003..0.109 rows=990 loops=1)
                                             Filter: (deleted_at IS NULL)
                                             Rows Removed by Filter: 10
                                             Buffers: shared hit=16
                           ->  Index Scan using pk_articles on articles a1_0  (cost=0.29..0.37 rows=1 width=41) (actual time=0.001..0.001 rows=1 loops=990)
                                 Index Cond: (id = c1_0.article_id)
                                 Filter: (deleted_at IS NULL)
                                 Rows Removed by Filter: 0
                                 Buffers: shared hit=2970
         SubPlan 1
           ->  Aggregate  (cost=24.93..24.94 rows=1 width=8) (actual time=0.009..0.009 rows=1 loops=10)
                 Buffers: shared hit=160
                 ->  Nested Loop  (cost=0.85..24.93 rows=1 width=16) (actual time=0.006..0.008 rows=2 loops=10)
                       Buffers: shared hit=160
                       ->  Nested Loop  (cost=0.58..16.62 rows=1 width=32) (actual time=0.004..0.005 rows=2 loops=10)
                             Buffers: shared hit=100
                             ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl2_0  (cost=0.29..8.30 rows=1 width=48) (actual time=0.002..0.002 rows=2 loops=10)
                                   Index Cond: (comment_id = c1_0.id)
                                   Buffers: shared hit=40
                             ->  Index Only Scan using pk_comments on comments c2_0  (cost=0.29..8.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                                   Index Cond: (id = c1_0.id)
                                   Heap Fetches: 20
                                   Buffers: shared hit=60
                       ->  Index Scan using pk_users on users lb2_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                             Index Cond: (id = cl2_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=60
 Planning:
   Buffers: shared hit=348
 Planning Time: 1.019 ms
 Execution Time: 8.936 ms
(67 rows)
```

</details>

<details>
<summary>1m - 최근 좋아요한 댓글</summary>

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=5911.11..6207.20 rows=10 width=159) (actual time=8.275..10.256 rows=10 loops=1)
   Buffers: shared hit=3484
   ->  Nested Loop  (cost=5911.11..283461.15 rows=9374 width=159) (actual time=8.273..10.252 rows=10 loops=1)
         Buffers: shared hit=3484
         ->  Nested Loop  (cost=5910.83..47048.82 rows=9374 width=167) (actual time=8.217..10.145 rows=10 loops=1)
               Buffers: shared hit=3291
               ->  Nested Loop  (cost=5910.54..44165.39 rows=9468 width=153) (actual time=8.209..10.130 rows=10 loops=1)
                     Buffers: shared hit=3261
                     ->  Nested Loop  (cost=5910.12..39346.70 rows=9648 width=127) (actual time=8.172..10.082 rows=10 loops=1)
                           Buffers: shared hit=3221
                           ->  Gather Merge  (cost=5909.70..7089.50 rows=10130 width=56) (actual time=8.112..10.010 rows=10 loops=1)
                                 Workers Planned: 2
                                 Workers Launched: 2
                                 Buffers: shared hit=3181
                                 ->  Sort  (cost=4909.68..4920.23 rows=4221 width=56) (actual time=6.018..6.019 rows=3 loops=3)
                                       Sort Key: cl1_0.created_at DESC, cl1_0.id DESC
                                       Sort Method: quicksort  Memory: 1166kB
                                       Buffers: shared hit=3181
                                       Worker 0:  Sort Method: quicksort  Memory: 25kB
                                       Worker 1:  Sort Method: quicksort  Memory: 25kB
                                       ->  Parallel Seq Scan on comment_likes cl1_0  (cost=0.00..4655.50 rows=4221 width=56) (actual time=3.360..5.608 rows=3333 loops=3)
                                             Filter: (liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                             Rows Removed by Filter: 96667
                                             Buffers: shared hit=3093
                           ->  Index Scan using pk_comments on comments c1_0  (cost=0.42..3.18 rows=1 width=87) (actual time=0.003..0.003 rows=1 loops=10)
                                 Index Cond: (id = cl1_0.comment_id)
                                 Filter: (deleted_at IS NULL)
                                 Buffers: shared hit=40
                     ->  Index Scan using pk_articles on articles a1_0  (cost=0.42..0.50 rows=1 width=42) (actual time=0.004..0.004 rows=1 loops=10)
                           Index Cond: (id = c1_0.article_id)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=40
               ->  Index Scan using pk_users on users u1_0  (cost=0.29..0.30 rows=1 width=30) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.user_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=30
         ->  Materialize  (cost=0.29..8.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users lb1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.002..0.003 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=25.20..25.21 rows=1 width=8) (actual time=0.009..0.009 rows=1 loops=10)
                 Buffers: shared hit=190
                 ->  Nested Loop  (cost=1.13..25.19 rows=1 width=16) (actual time=0.005..0.008 rows=2 loops=10)
                       Buffers: shared hit=190
                       ->  Nested Loop  (cost=0.84..16.89 rows=1 width=32) (actual time=0.004..0.006 rows=2 loops=10)
                             Buffers: shared hit=130
                             ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl2_0  (cost=0.42..8.44 rows=1 width=48) (actual time=0.002..0.003 rows=2 loops=10)
                                   Index Cond: (comment_id = c1_0.id)
                                   Buffers: shared hit=50
                             ->  Index Only Scan using pk_comments on comments c2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                                   Index Cond: (id = c1_0.id)
                                   Heap Fetches: 20
                                   Buffers: shared hit=80
                       ->  Index Scan using pk_users on users lb2_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                             Index Cond: (id = cl2_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=60
 Planning:
   Buffers: shared hit=353
 Planning Time: 1.130 ms
 Execution Time: 10.515 ms
(64 rows)
```

</details>

<details>
<summary>10m - 최근 좋아요한 댓글</summary>

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=47824.92..48166.41 rows=10 width=162) (actual time=41.854..43.836 rows=10 loops=1)
   Buffers: shared hit=808 read=30542
   ->  Nested Loop  (cost=47824.92..384738.72 rows=9866 width=162) (actual time=41.852..43.832 rows=10 loops=1)
         Buffers: shared hit=808 read=30542
         ->  Nested Loop  (cost=47824.50..134477.60 rows=9866 width=170) (actual time=41.770..43.695 rows=10 loops=1)
               Buffers: shared hit=594 read=30542
               ->  Nested Loop  (cost=47824.08..130123.92 rows=9962 width=155) (actual time=41.760..43.675 rows=10 loops=1)
                     Buffers: shared hit=554 read=30542
                     ->  Nested Loop  (cost=47823.66..124961.67 rows=10166 width=128) (actual time=41.746..43.651 rows=10 loops=1)
                           Buffers: shared hit=514 read=30542
                           ->  Gather Merge  (cost=47823.23..49069.42 rows=10700 width=56) (actual time=41.712..43.605 rows=10 loops=1)
                                 Workers Planned: 2
                                 Workers Launched: 2
                                 Buffers: shared hit=474 read=30542
                                 ->  Sort  (cost=46823.20..46834.35 rows=4458 width=56) (actual time=39.813..39.814 rows=3 loops=3)
                                       Sort Key: cl1_0.created_at DESC, cl1_0.id DESC
                                       Sort Method: quicksort  Memory: 1166kB
                                       Buffers: shared hit=474 read=30542
                                       Worker 0:  Sort Method: quicksort  Memory: 25kB
                                       Worker 1:  Sort Method: quicksort  Memory: 25kB
                                       ->  Parallel Seq Scan on comment_likes cl1_0  (cost=0.00..46553.00 rows=4458 width=56) (actual time=25.990..39.452 rows=3333 loops=3)
                                             Filter: (liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                             Rows Removed by Filter: 996667
                                             Buffers: shared hit=386 read=30542
                           ->  Index Scan using pk_comments on comments c1_0  (cost=0.43..7.09 rows=1 width=88) (actual time=0.003..0.003 rows=1 loops=10)
                                 Index Cond: (id = cl1_0.comment_id)
                                 Filter: (deleted_at IS NULL)
                                 Buffers: shared hit=40
                     ->  Index Scan using pk_articles on articles a1_0  (cost=0.43..0.51 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
                           Index Cond: (id = c1_0.article_id)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=40
               ->  Index Scan using pk_users on users u1_0  (cost=0.42..0.44 rows=1 width=31) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.user_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=4
               ->  Index Scan using pk_users on users lb1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.009..0.009 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
         SubPlan 1
           ->  Aggregate  (cost=25.34..25.35 rows=1 width=8) (actual time=0.009..0.009 rows=1 loops=10)
                 Buffers: shared hit=210
                 ->  Nested Loop  (cost=1.28..25.34 rows=1 width=16) (actual time=0.005..0.008 rows=2 loops=10)
                       Buffers: shared hit=210
                       ->  Nested Loop  (cost=0.86..16.91 rows=1 width=32) (actual time=0.004..0.006 rows=2 loops=10)
                             Buffers: shared hit=130
                             ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl2_0  (cost=0.43..8.45 rows=1 width=48) (actual time=0.002..0.002 rows=2 loops=10)
                                   Index Cond: (comment_id = c1_0.id)
                                   Buffers: shared hit=50
                             ->  Index Only Scan using pk_comments on comments c2_0  (cost=0.43..8.45 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                                   Index Cond: (id = c1_0.id)
                                   Heap Fetches: 20
                                   Buffers: shared hit=80
                       ->  Index Scan using pk_users on users lb2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                             Index Cond: (id = cl2_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=80
 Planning:
   Buffers: shared hit=357
 Planning Time: 1.154 ms
 Execution Time: 44.102 ms
(64 rows)
```

</details>
