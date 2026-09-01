# 최근 좋아요한 댓글 재측정

> [MID4-134 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 사용자별 최근 좋아요 인덱스를 적용하자 가장 큰 테스트 데이터의 실행 시간 가운데 값이 `45.905 ms`에서 `0.684 ms`로 98.51% 줄었다.
- DB가 전체 좋아요를 넓게 읽고 정렬하는 대신 대상 사용자의 최신 좋아요 10건부터 찾게 됐다.
- 댓글별 좋아요 수를 세는 하위 조회는 남아 있지만 현재 결과는 모두 1ms 미만이어서 이번 작업에서는 분리하지 않았다.

아래부터는 이 결론의 측정값과 실행계획을 기록한다.

MID4-133 인덱스 적용 후 `comment_likes.liked_by` 접근과 최신순 정렬이 `idx_comment_likes_liked_by_created_id`로 처리된다. 10m seed scale median은 `0.684 ms`로 baseline `45.905 ms` 대비 `98.51%` 감소했다.

또한 이 조회는 최근 좋아요한 댓글 10건만 반환하므로 댓글 좋아요 수 count subquery도 최종 반환 row 기준으로 최대 10회 반복된다. optimized 측정에서 100k, 1m, 10m scale의 median이 `0.671 ms`, `0.804 ms`, `0.684 ms`로 모두 1ms 미만이고 scale 증가에 따른 뚜렷한 악화가 보이지 않는다. 따라서 count subquery를 별도 쿼리로 분리할 근거는 현재 수치상 부족하다. 별도 count 쿼리로 나누면 SQL 자체는 단순해질 수 있지만, 추가 round-trip과 애플리케이션 조립 비용이 생기므로 MID4-134에서는 현재 쿼리 구조를 유지하고 인덱스 적용 결과만 기록한다.

## 어떤 인덱스를 적용했나

| index | columns | 목적 |
| --- | --- | --- |
| `idx_comment_likes_liked_by_created_id` | `comment_likes(liked_by, created_at DESC, id DESC)` | 좋아요 사용자 필터와 최신순 정렬 |

## 측정 기준

- raw: [raw/rerun-132-method](raw/rerun-132-method)
- SQL template: [raw/rerun-132-method/sql-template.sql](raw/rerun-132-method/sql-template.sql)
- 측정 방식: seed 후 ANALYZE, 조회별 3회 warm-up, EXPLAIN (ANALYZE, BUFFERS), 동일 SQL 5회 반복 실행 후 median 기록
- 측정 SQL: 기존 Hibernate SQL 기준이며, userId binding만 00000001-0000-4000-8000-000000000001 literal로 치환

## 실행 시간

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `8.936` ms | `0.215` ms | `9.066`, `9.725`, `11.235`, `9.524`, `8.936` ms | `0.753`, `0.671`, `0.645`, `0.688`, `0.656` ms | `9.524` ms | `0.671` ms |
| `1m` | `10.515` ms | `0.138` ms | `12.238`, `11.750`, `12.216`, `11.807`, `11.632` ms | `0.902`, `0.735`, `0.770`, `0.836`, `0.804` ms | `11.807` ms | `0.804` ms |
| `10m` | `44.102` ms | `0.169` ms | `48.387`, `52.918`, `45.801`, `44.662`, `45.905` ms | `0.684`, `0.832`, `0.679`, `0.654`, `0.717` ms | `45.905` ms | `0.684` ms |

## 실행 시간 가운데 값 비교

| seed scale | baseline median | optimized median | delta | change |
| --- | ---: | ---: | ---: | ---: |
| `100k` | `9.524` ms | `0.671` ms | `-8.853` ms | `-92.95`% |
| `1m` | `11.807` ms | `0.804` ms | `-11.003` ms | `-93.19`% |
| `10m` | `45.905` ms | `0.684` ms | `-45.221` ms | `-98.51`% |

## DB가 데이터를 찾은 방법

| seed scale | 주요 접근 경로 | Execution Time |
| --- | --- | ---: |
| `100k` | `idx_comment_likes_liked_by_created_id`, `pk_comments`, `pk_articles`, `pk_users`, `uk_comment_likes_comment_liked_by` | `0.215` ms |
| `1m` | `idx_comment_likes_liked_by_created_id`, `pk_comments`, `pk_articles`, `pk_users`, `uk_comment_likes_comment_liked_by` | `0.138` ms |
| `10m` | `idx_comment_likes_liked_by_created_id`, `pk_comments`, `pk_articles`, `pk_users`, `uk_comment_likes_comment_liked_by` | `0.169` ms |

## 실행계획 원문

<details>
<summary>100k - 최근 좋아요한 댓글</summary>

```text
                                                                                           QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.42..304.97 rows=10 width=156) (actual time=0.037..0.145 rows=10 loops=1)
   Buffers: shared hit=256
   ->  Nested Loop  (cost=1.42..28080.51 rows=925 width=156) (actual time=0.037..0.143 rows=10 loops=1)
         Buffers: shared hit=256
         ->  Nested Loop  (cost=1.14..4987.75 rows=925 width=164) (actual time=0.018..0.081 rows=10 loops=1)
               Buffers: shared hit=93
               ->  Nested Loop  (cost=0.86..4712.12 rows=935 width=151) (actual time=0.014..0.071 rows=10 loops=1)
                     Buffers: shared hit=63
                     ->  Nested Loop  (cost=0.58..4362.33 rows=953 width=126) (actual time=0.011..0.060 rows=10 loops=1)
                           Buffers: shared hit=33
                           ->  Index Scan using idx_comment_likes_liked_by_created_id on comment_likes cl1_0  (cost=0.29..1278.83 rows=1000 width=56) (actual time=0.007..0.008 rows=10 loops=1)
                                 Index Cond: (liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Buffers: shared hit=3
                           ->  Index Scan using pk_comments on comments c1_0  (cost=0.29..3.08 rows=1 width=86) (actual time=0.005..0.005 rows=1 loops=10)
                                 Index Cond: (id = cl1_0.comment_id)
                                 Filter: (deleted_at IS NULL)
                                 Buffers: shared hit=30
                     ->  Index Scan using pk_articles on articles a1_0  (cost=0.29..0.37 rows=1 width=41) (actual time=0.001..0.001 rows=1 loops=10)
                           Index Cond: (id = c1_0.article_id)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=30
               ->  Index Scan using pk_users on users u1_0  (cost=0.28..0.29 rows=1 width=29) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.user_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=30
         ->  Materialize  (cost=0.28..8.30 rows=1 width=16) (actual time=0.000..0.001 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users lb1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.002..0.003 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=24.93..24.94 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                 Buffers: shared hit=160
                 ->  Nested Loop  (cost=0.85..24.93 rows=1 width=16) (actual time=0.003..0.005 rows=2 loops=10)
                       Buffers: shared hit=160
                       ->  Nested Loop  (cost=0.58..16.62 rows=1 width=32) (actual time=0.002..0.003 rows=2 loops=10)
                             Buffers: shared hit=100
                             ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl2_0  (cost=0.29..8.30 rows=1 width=48) (actual time=0.001..0.001 rows=2 loops=10)
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
   Buffers: shared hit=47
 Planning Time: 0.384 ms
 Execution Time: 0.215 ms
(53 rows)
```

</details>
<details>
<summary>1m - 최근 좋아요한 댓글</summary>

```text
                                                                                            QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.83..310.53 rows=10 width=159) (actual time=0.035..0.099 rows=10 loops=1)
   Buffers: shared hit=307
   ->  Nested Loop  (cost=1.83..286996.34 rows=9297 width=159) (actual time=0.035..0.098 rows=10 loops=1)
         Buffers: shared hit=307
         ->  Nested Loop  (cost=1.55..52525.88 rows=9297 width=167) (actual time=0.016..0.038 rows=10 loops=1)
               Buffers: shared hit=114
               ->  Nested Loop  (cost=1.26..49665.58 rows=9392 width=153) (actual time=0.013..0.029 rows=10 loops=1)
                     Buffers: shared hit=84
                     ->  Nested Loop  (cost=0.84..44886.61 rows=9568 width=127) (actual time=0.010..0.019 rows=10 loops=1)
                           Buffers: shared hit=44
                           ->  Index Scan using idx_comment_likes_liked_by_created_id on comment_likes cl1_0  (cost=0.42..12756.61 rows=10050 width=56) (actual time=0.006..0.006 rows=10 loops=1)
                                 Index Cond: (liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Buffers: shared hit=4
                           ->  Index Scan using pk_comments on comments c1_0  (cost=0.42..3.20 rows=1 width=87) (actual time=0.001..0.001 rows=1 loops=10)
                                 Index Cond: (id = cl1_0.comment_id)
                                 Filter: (deleted_at IS NULL)
                                 Buffers: shared hit=40
                     ->  Index Scan using pk_articles on articles a1_0  (cost=0.42..0.50 rows=1 width=42) (actual time=0.001..0.001 rows=1 loops=10)
                           Index Cond: (id = c1_0.article_id)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=40
               ->  Index Scan using pk_users on users u1_0  (cost=0.29..0.30 rows=1 width=30) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.user_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=30
         ->  Materialize  (cost=0.29..8.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users lb1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.004..0.004 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=25.20..25.21 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                 Buffers: shared hit=190
                 ->  Nested Loop  (cost=1.13..25.19 rows=1 width=16) (actual time=0.003..0.005 rows=2 loops=10)
                       Buffers: shared hit=190
                       ->  Nested Loop  (cost=0.84..16.89 rows=1 width=32) (actual time=0.002..0.003 rows=2 loops=10)
                             Buffers: shared hit=130
                             ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl2_0  (cost=0.42..8.44 rows=1 width=48) (actual time=0.001..0.001 rows=2 loops=10)
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
   Buffers: shared hit=56
 Planning Time: 0.484 ms
 Execution Time: 0.138 ms
(53 rows)
```

</details>
<details>
<summary>10m - 최근 좋아요한 댓글</summary>

```text
                                                                                            QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=2.12..382.13 rows=10 width=162) (actual time=0.036..0.135 rows=10 loops=1)
   Buffers: shared hit=338
   ->  Nested Loop  (cost=2.12..325820.73 rows=8574 width=162) (actual time=0.036..0.133 rows=10 loops=1)
         Buffers: shared hit=338
         ->  Nested Loop  (cost=1.71..108331.40 rows=8574 width=170) (actual time=0.015..0.040 rows=10 loops=1)
               Buffers: shared hit=124
               ->  Nested Loop  (cost=1.29..104548.47 rows=8656 width=155) (actual time=0.013..0.030 rows=10 loops=1)
                     Buffers: shared hit=84
                     ->  Nested Loop  (cost=0.86..100062.44 rows=8834 width=128) (actual time=0.010..0.020 rows=10 loops=1)
                           Buffers: shared hit=44
                           ->  Index Scan using idx_comment_likes_liked_by_created_id on comment_likes cl1_0  (cost=0.43..32768.69 rows=9300 width=56) (actual time=0.006..0.007 rows=10 loops=1)
                                 Index Cond: (liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                 Buffers: shared hit=4
                           ->  Index Scan using pk_comments on comments c1_0  (cost=0.43..7.24 rows=1 width=88) (actual time=0.001..0.001 rows=1 loops=10)
                                 Index Cond: (id = cl1_0.comment_id)
                                 Filter: (deleted_at IS NULL)
                                 Buffers: shared hit=40
                     ->  Index Scan using pk_articles on articles a1_0  (cost=0.43..0.51 rows=1 width=43) (actual time=0.001..0.001 rows=1 loops=10)
                           Index Cond: (id = c1_0.article_id)
                           Filter: (deleted_at IS NULL)
                           Buffers: shared hit=40
               ->  Index Scan using pk_users on users u1_0  (cost=0.42..0.44 rows=1 width=31) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.user_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.000..0.001 rows=1 loops=10)
               Buffers: shared hit=4
               ->  Index Scan using pk_users on users lb1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.003..0.003 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
         SubPlan 1
           ->  Aggregate  (cost=25.34..25.35 rows=1 width=8) (actual time=0.008..0.008 rows=1 loops=10)
                 Buffers: shared hit=210
                 ->  Nested Loop  (cost=1.28..25.34 rows=1 width=16) (actual time=0.005..0.008 rows=2 loops=10)
                       Buffers: shared hit=210
                       ->  Nested Loop  (cost=0.86..16.91 rows=1 width=32) (actual time=0.004..0.006 rows=2 loops=10)
                             Buffers: shared hit=130
                             ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl2_0  (cost=0.43..8.45 rows=1 width=48) (actual time=0.003..0.003 rows=2 loops=10)
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
   Buffers: shared hit=60
 Planning Time: 0.439 ms
 Execution Time: 0.169 ms
(53 rows)
```

</details>
