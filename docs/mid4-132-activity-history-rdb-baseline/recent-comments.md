# 최근 작성 댓글

> [MID4-132 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 이 조회는 사용자가 최근에 작성한 댓글 10건과 각 댓글의 좋아요 수를 가져온다.
- 데이터가 커지자 실행 시간의 가운데 값이 `8.983 ms`에서 `82.747 ms`로 늘었다.
- DB가 대상 사용자의 댓글만 바로 찾지 못하고 댓글 테이블을 넓게 읽은 뒤 최신순으로 정렬한 것이 주된 원인이다.
- 사용자와 최신순을 함께 처리하는 인덱스를 다음 개선 후보로 정했다.

아래부터는 이 판단을 뒷받침하는 SQL과 실행계획을 기록한다.

이 문서의 `100k`, `1m`, `10m`은 각 테이블 row 수가 아니라 `seed_activity_history(scale_count)`에 전달한 seed scale이다. 실제 테이블별 row count는 README의 Seed 결과 표를 기준으로 본다.

최근 작성 댓글은 `comments.user_id` FK 제약은 있지만 PostgreSQL에서 해당 FK 컬럼 기준 인덱스가 자동 생성되지 않아 사용자 조건으로 먼저 좁히지 못하고, 이후 `created_at DESC, id DESC` 정렬 비용이 발생한다. `deleted_at IS NULL` filter는 남지만 현재 실행계획에서는 조회 경로와 정렬 비용이 더 큰 병목으로 보이므로 1차 인덱스 후보에서는 제외한다.

## 다음에 확인할 인덱스

- 후보: `comments(user_id, created_at DESC, id DESC)` 복합 인덱스를 적용해 `Seq Scan` 또는 `Parallel Seq Scan`과 `top-N heapsort` 제거 여부를 확인한다.
- 기대 효과: 대상 사용자 댓글을 먼저 좁힌 뒤 최신순 정렬을 인덱스 순서로 처리한다.
- `deleted_at IS NULL` 단독 인덱스 또는 partial index는 복합 인덱스 적용 후 filter 비용이 남는 경우 추가 측정한다.

## 실제로 실행한 SQL

### 최근 작성 댓글

`MID4-93`의 `CommentRepository.findRecentCommentActivities()` JPQL을 임시 Spring Boot test에서 실행하고 Hibernate SQL 로그로 캡처한 실제 query다. 아래 SQL은 측정을 위해 `userId` binding만 literal로 치환했다.

```sql
SELECT
    c1_0.id,
    a1_0.id,
    a1_0.title,
    u1_0.id,
    u1_0.nickname,
    c1_0.content,
    (
        SELECT COUNT(cl1_0.id)
        FROM comment_likes cl1_0
        JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by
        WHERE cl1_0.comment_id = c1_0.id
          AND lb1_0.deleted_at IS NULL
    ),
    c1_0.created_at
FROM comments c1_0
JOIN users u1_0 ON u1_0.id = c1_0.user_id
JOIN articles a1_0 ON a1_0.id = c1_0.article_id
WHERE u1_0.id = '00000001-0000-4000-8000-000000000001'
  AND u1_0.deleted_at IS NULL
  AND c1_0.deleted_at IS NULL
  AND a1_0.deleted_at IS NULL
ORDER BY c1_0.created_at DESC, c1_0.id DESC
FETCH FIRST 10 ROWS ONLY;
```

## 데이터 크기별 실행 시간

| Seed scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 최근 작성 댓글 | `7.736 ms` | `8.953`, `8.983`, `11.553`, `9.165`, `8.712 ms` | `8.983 ms` |
| `1m` | 최근 작성 댓글 | `12.836 ms` | `14.151`, `13.587`, `13.720`, `17.696`, `14.005 ms` | `14.005 ms` |
| `10m` | 최근 작성 댓글 | `88.314 ms` | `80.545`, `82.747`, `95.963`, `93.401`, `78.940 ms` | `82.747 ms` |

## DB가 데이터를 찾은 방법

### 100k

최근 작성 댓글:

- `comments`는 `user_id`와 `deleted_at` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `39000`
- 정렬은 `Sort Method: top-N heapsort`로 `created_at DESC, id DESC`를 처리했다.
- 좋아요 수 subquery는 `uk_comment_likes_comment_liked_by` 인덱스를 사용했다.
- subquery는 `comment_likes` 풀 테이블 스캔을 하지 않았다.

### 1m

최근 작성 댓글:

- `comments`는 `Parallel Seq Scan`으로 처리됐다.
- worker당 `Rows Removed by Filter`: `130000`, `loops=3`
- 좋아요 수 subquery는 `uk_comment_likes_comment_liked_by` 인덱스를 사용했다.

### 10m

최근 작성 댓글:

- `comments`는 `Parallel Seq Scan`으로 처리됐다.
- worker당 `Rows Removed by Filter`: `1330000`, `loops=3`
- 좋아요 수 subquery는 `uk_comment_likes_comment_liked_by` 인덱스를 사용했다.

## 실행계획 원문

<details>
<summary>100k - 최근 작성 댓글</summary>

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1948.02..2114.41 rows=10 width=132) (actual time=7.653..7.677 rows=10 loops=1)
   Buffers: shared hit=1161
   ->  Result  (cost=1948.02..17139.14 rows=913 width=132) (actual time=7.652..7.674 rows=10 loops=1)
         Buffers: shared hit=1161
         ->  Sort  (cost=1948.02..1950.30 rows=913 width=124) (actual time=7.609..7.611 rows=10 loops=1)
               Sort Key: c1_0.created_at DESC, c1_0.id DESC
               Sort Method: top-N heapsort  Memory: 27kB
               Buffers: shared hit=1101
               ->  Nested Loop  (cost=1091.91..1928.29 rows=913 width=124) (actual time=2.964..7.484 rows=1000 loops=1)
                     Buffers: shared hit=1095
                     ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=29) (actual time=0.004..0.006 rows=1 loops=1)
                           Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=3
                     ->  Hash Join  (cost=1091.64..1910.87 rows=913 width=111) (actual time=2.958..7.420 rows=1000 loops=1)
                           Hash Cond: (a1_0.id = c1_0.article_id)
                           Buffers: shared hit=1092
                           ->  Seq Scan on articles a1_0  (cost=0.00..712.00 rows=19620 width=41) (actual time=0.003..3.366 rows=19620 loops=1)
                                 Filter: (deleted_at IS NULL)
                                 Rows Removed by Filter: 380
                                 Buffers: shared hit=512
                           ->  Hash  (cost=1080.00..1080.00 rows=931 width=86) (actual time=2.946..2.947 rows=1000 loops=1)
                                 Buckets: 1024  Batches: 1  Memory Usage: 126kB
                                 Buffers: shared hit=580
                                 ->  Seq Scan on comments c1_0  (cost=0.00..1080.00 rows=931 width=86) (actual time=0.003..2.843 rows=1000 loops=1)
                                       Filter: ((deleted_at IS NULL) AND (user_id = '00000001-0000-4000-8000-000000000001'::uuid))
                                       Rows Removed by Filter: 39000
                                       Buffers: shared hit=580
         SubPlan 1
           ->  Aggregate  (cost=16.62..16.63 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                 Buffers: shared hit=60
                 ->  Nested Loop  (cost=0.56..16.61 rows=1 width=16) (actual time=0.004..0.005 rows=1 loops=10)
                       Buffers: shared hit=60
                       ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl1_0  (cost=0.29..8.30 rows=1 width=32) (actual time=0.003..0.003 rows=1 loops=10)
                             Index Cond: (comment_id = c1_0.id)
                             Buffers: shared hit=30
                       ->  Index Scan using pk_users on users lb1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (id = cl1_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
 Planning:
   Buffers: shared hit=330
 Planning Time: 0.813 ms
 Execution Time: 7.736 ms
(44 rows)
```

</details>

<details>
<summary>1m - 최근 작성 댓글</summary>

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=9116.76..9314.67 rows=10 width=135) (actual time=10.992..12.667 rows=10 loops=1)
   Buffers: shared hit=863 read=5136
   ->  Nested Loop  (cost=9116.76..192364.31 rows=9259 width=135) (actual time=10.990..12.663 rows=10 loops=1)
         Buffers: shared hit=863 read=5136
         ->  Nested Loop  (cost=9116.47..37090.72 rows=9259 width=113) (actual time=10.941..12.589 rows=10 loops=1)
               Buffers: shared hit=790 read=5136
               ->  Gather Merge  (cost=9116.05..10214.91 rows=9435 width=87) (actual time=10.914..12.548 rows=10 loops=1)
                     Workers Planned: 2
                     Workers Launched: 2
                     Buffers: shared hit=750 read=5136
                     ->  Sort  (cost=8116.03..8125.85 rows=3931 width=87) (actual time=8.992..8.996 rows=117 loops=3)
                           Sort Key: c1_0.created_at DESC, c1_0.id DESC
                           Sort Method: quicksort  Memory: 1441kB
                           Buffers: shared hit=750 read=5136
                           Worker 0:  Sort Method: quicksort  Memory: 25kB
                           Worker 1:  Sort Method: quicksort  Memory: 62kB
                           ->  Parallel Seq Scan on comments c1_0  (cost=0.00..7881.33 rows=3931 width=87) (actual time=2.539..8.591 rows=3333 loops=3)
                                 Filter: ((deleted_at IS NULL) AND (user_id = '00000001-0000-4000-8000-000000000001'::uuid))
                                 Rows Removed by Filter: 130000
                                 Buffers: shared hit=662 read=5136
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.42..2.85 rows=1 width=42) (actual time=0.003..0.003 rows=1 loops=10)
                     Index Cond: (id = c1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.29..8.31 rows=1 width=30) (actual time=0.002..0.002 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=30) (actual time=0.013..0.013 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=16.75..16.76 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                 Buffers: shared hit=70
                 ->  Nested Loop  (cost=0.71..16.74 rows=1 width=16) (actual time=0.004..0.004 rows=1 loops=10)
                       Buffers: shared hit=70
                       ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl1_0  (cost=0.42..8.44 rows=1 width=32) (actual time=0.002..0.003 rows=1 loops=10)
                             Index Cond: (comment_id = c1_0.id)
                             Buffers: shared hit=40
                       ->  Index Scan using pk_users on users lb1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (id = cl1_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
 Planning:
   Buffers: shared hit=332
 Planning Time: 0.736 ms
 Execution Time: 12.836 ms
(46 rows)
```

</details>

<details>
<summary>10m - 최근 작성 댓글</summary>

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=82788.68..83026.83 rows=10 width=138) (actual time=86.276..88.130 rows=10 loops=1)
   Buffers: shared hit=663 read=60283
   ->  Nested Loop  (cost=82788.68..292698.23 rows=8814 width=138) (actual time=86.274..88.126 rows=10 loops=1)
         Buffers: shared hit=663 read=60283
         ->  Nested Loop  (cost=82788.26..143665.66 rows=8814 width=115) (actual time=86.203..88.029 rows=10 loops=1)
               Buffers: shared hit=579 read=60283
               ->  Gather Merge  (cost=82787.83..83835.33 rows=8994 width=88) (actual time=86.172..87.985 rows=10 loops=1)
                     Workers Planned: 2
                     Workers Launched: 2
                     Buffers: shared hit=539 read=60283
                     ->  Sort  (cost=81787.81..81797.18 rows=3748 width=88) (actual time=84.010..84.012 rows=3 loops=3)
                           Sort Key: c1_0.created_at DESC, c1_0.id DESC
                           Sort Method: quicksort  Memory: 1478kB
                           Buffers: shared hit=539 read=60283
                           Worker 0:  Sort Method: quicksort  Memory: 25kB
                           Worker 1:  Sort Method: quicksort  Memory: 25kB
                           ->  Parallel Seq Scan on comments c1_0  (cost=0.00..81565.33 rows=3748 width=88) (actual time=55.371..83.556 rows=3333 loops=3)
                                 Filter: ((deleted_at IS NULL) AND (user_id = '00000001-0000-4000-8000-000000000001'::uuid))
                                 Rows Removed by Filter: 1330000
                                 Buffers: shared hit=451 read=60283
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.43..6.65 rows=1 width=43) (actual time=0.003..0.003 rows=1 loops=10)
                     Index Cond: (id = c1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=4
               ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=31) (actual time=0.009..0.010 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
         SubPlan 1
           ->  Aggregate  (cost=16.89..16.90 rows=1 width=8) (actual time=0.007..0.007 rows=1 loops=10)
                 Buffers: shared hit=80
                 ->  Nested Loop  (cost=0.85..16.88 rows=1 width=16) (actual time=0.006..0.006 rows=1 loops=10)
                       Buffers: shared hit=80
                       ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl1_0  (cost=0.43..8.45 rows=1 width=32) (actual time=0.005..0.005 rows=1 loops=10)
                             Index Cond: (comment_id = c1_0.id)
                             Buffers: shared hit=40
                       ->  Index Scan using pk_users on users lb1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (id = cl1_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=40
 Planning:
   Buffers: shared hit=334
 Planning Time: 0.815 ms
 Execution Time: 88.314 ms
(46 rows)
```

</details>
