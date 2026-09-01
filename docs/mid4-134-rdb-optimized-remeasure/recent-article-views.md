# 최근 조회 기사 재측정

> [MID4-134 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 사용자별 최근 조회와 기사별 댓글 인덱스를 적용하자 가장 큰 테스트 데이터의 실행 시간 가운데 값이 `1,825.932 ms`에서 `0.525 ms`로 99.97% 줄었다.
- 개선 전에는 조회 기록과 댓글을 넓게 반복해서 읽었지만, 개선 후에는 필요한 사용자와 기사 데이터부터 인덱스로 찾았다.
- 기사별 댓글 수와 조회 수를 세는 하위 조회는 남아 있지만 현재 결과가 1ms 미만이어서 이번 작업에서는 분리하지 않았다.

아래부터는 이 결론의 측정값과 실행계획을 기록한다.

MID4-132 baseline의 가장 큰 SQL 병목 후보였던 최근 조회 기사는 `article_views.user_id` 접근과 `comments.article_id` subquery 접근이 모두 인덱스를 사용한다. 10m seed scale median은 `0.525 ms`로 baseline `1825.932 ms` 대비 `99.97%` 감소했다.

또한 이 조회는 최신 조회 기사 10건만 반환하므로 댓글 수와 조회 수 subquery도 최종 반환 row 기준으로 최대 10회 반복된다. optimized 측정에서 100k, 1m, 10m scale의 median 차이가 크지 않고 모두 1ms 미만이므로, count subquery를 별도 쿼리로 분리할 근거는 현재 수치상 부족하다. 별도 count 쿼리로 나누면 SQL 자체는 단순해질 수 있지만, 추가 round-trip과 애플리케이션 조립 비용이 생기므로 MID4-134에서는 현재 쿼리 구조를 유지하고 인덱스 적용 결과만 기록한다.

## 어떤 인덱스를 적용했나

| index | columns | 목적 |
| --- | --- | --- |
| `idx_article_views_user_viewed_id` | `article_views(user_id, viewed_at DESC, id DESC)` | 사용자 필터와 최신순 정렬 |
| `idx_comments_article` | `comments(article_id)` | 기사별 댓글 수 subquery 접근 |

## 측정 기준

- raw: [raw/rerun-132-method](raw/rerun-132-method)
- SQL template: [raw/rerun-132-method/sql-template.sql](raw/rerun-132-method/sql-template.sql)
- 측정 방식: seed 후 ANALYZE, 조회별 3회 warm-up, EXPLAIN (ANALYZE, BUFFERS), 동일 SQL 5회 반복 실행 후 median 기록
- 측정 SQL: 기존 Hibernate SQL 기준이며, userId binding만 00000001-0000-4000-8000-000000000001 literal로 치환

## 실행 시간

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `25.568` ms | `0.190` ms | `25.981`, `27.132`, `27.095`, `27.150`, `26.654` ms | `0.839`, `0.639`, `0.711`, `0.500`, `0.645` ms | `27.095` ms | `0.645` ms |
| `1m` | `235.410` ms | `0.111` ms | `236.583`, `226.549`, `231.003`, `234.893`, `226.372` ms | `0.607`, `0.472`, `0.591`, `0.539`, `0.463` ms | `231.003` ms | `0.539` ms |
| `10m` | `1820.813` ms | `0.113` ms | `1825.932`, `1805.697`, `1861.183`, `1813.368`, `1832.137` ms | `0.477`, `0.599`, `0.525`, `0.493`, `0.622` ms | `1825.932` ms | `0.525` ms |

## 실행 시간 가운데 값 비교

| seed scale | baseline median | optimized median | delta | change |
| --- | ---: | ---: | ---: | ---: |
| `100k` | `27.095` ms | `0.645` ms | `-26.450` ms | `-97.62`% |
| `1m` | `231.003` ms | `0.539` ms | `-230.464` ms | `-99.77`% |
| `10m` | `1825.932` ms | `0.525` ms | `-1825.407` ms | `-99.97`% |

## DB가 데이터를 찾은 방법

| seed scale | 주요 접근 경로 | Execution Time |
| --- | --- | ---: |
| `100k` | `idx_article_views_user_viewed_id`, `pk_articles`, `pk_users`, `idx_comments_article`, `idx_article_views_article_viewed` | `0.190` ms |
| `1m` | `idx_article_views_user_viewed_id`, `pk_articles`, `pk_users`, `idx_comments_article`, `uk_article_views_article_user` | `0.111` ms |
| `10m` | `idx_article_views_user_viewed_id`, `pk_articles`, `pk_users`, `idx_comments_article`, `uk_article_views_article_user` | `0.113` ms |

## 실행계획 원문

<details>
<summary>100k - 최근 조회 기사</summary>

```text
                                                                                  QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.97..439.18 rows=10 width=200) (actual time=0.037..0.165 rows=10 loops=1)
   Buffers: shared hit=217
   ->  Nested Loop  (cost=0.97..42988.90 rows=981 width=200) (actual time=0.037..0.164 rows=10 loops=1)
         Buffers: shared hit=217
         ->  Nested Loop  (cost=0.70..3304.10 rows=981 width=184) (actual time=0.006..0.016 rows=10 loops=1)
               Buffers: shared hit=34
               ->  Index Scan using idx_article_views_user_viewed_id on article_views av1_0  (cost=0.41..659.10 rows=1000 width=56) (actual time=0.004..0.005 rows=10 loops=1)
                     Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Buffers: shared hit=4
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.29..2.65 rows=1 width=144) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = av1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=30
         ->  Materialize  (cost=0.28..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.006..0.006 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=27.92..27.93 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Buffers: shared hit=100
                 ->  Nested Loop  (cost=0.56..27.91 rows=2 width=16) (actual time=0.002..0.003 rows=2 loops=10)
                       Buffers: shared hit=100
                       ->  Index Scan using idx_comments_article on comments c1_0  (cost=0.29..11.31 rows=2 width=32) (actual time=0.001..0.001 rows=2 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=40
                       ->  Index Scan using pk_users on users u2_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                             Index Cond: (id = c1_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=60
         SubPlan 2
           ->  Aggregate  (cost=12.49..12.50 rows=1 width=8) (actual time=0.010..0.010 rows=1 loops=10)
                 Buffers: shared hit=80
                 ->  Merge Join  (cost=12.15..12.49 rows=2 width=16) (actual time=0.009..0.009 rows=3 loops=10)
                       Merge Cond: (u3_0.id = av2_0.user_id)
                       Buffers: shared hit=80
                       ->  Index Scan using pk_users on users u3_0  (cost=0.28..58.27 rows=990 width=16) (actual time=0.004..0.004 rows=4 loops=10)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
                       ->  Sort  (cost=11.88..11.88 rows=2 width=32) (actual time=0.004..0.004 rows=3 loops=10)
                             Sort Key: av2_0.user_id
                             Sort Method: quicksort  Memory: 25kB
                             Buffers: shared hit=50
                             ->  Bitmap Heap Scan on article_views av2_0  (cost=4.30..11.87 rows=2 width=32) (actual time=0.002..0.003 rows=3 loops=10)
                                   Recheck Cond: (article_id = a1_0.id)
                                   Heap Blocks: exact=30
                                   Buffers: shared hit=50
                                   ->  Bitmap Index Scan on idx_article_views_article_viewed  (cost=0.00..4.30 rows=2 width=0) (actual time=0.001..0.001 rows=3 loops=10)
                                         Index Cond: (article_id = a1_0.id)
                                         Buffers: shared hit=20
 Planning:
   Buffers: shared hit=32
 Planning Time: 0.231 ms
 Execution Time: 0.190 ms
(56 rows)
```

</details>
<details>
<summary>1m - 최근 조회 기사</summary>

```text
                                                                                   QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.13..468.43 rows=10 width=203) (actual time=0.024..0.090 rows=10 loops=1)
   Buffers: shared hit=247
   ->  Nested Loop  (cost=1.13..458233.25 rows=9806 width=203) (actual time=0.023..0.089 rows=10 loops=1)
         Buffers: shared hit=247
         ->  Nested Loop  (cost=0.84..34279.19 rows=9806 width=187) (actual time=0.006..0.015 rows=10 loops=1)
               Buffers: shared hit=44
               ->  Index Scan using idx_article_views_user_viewed_id on article_views av1_0  (cost=0.42..6572.56 rows=9990 width=56) (actual time=0.004..0.005 rows=10 loops=1)
                     Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Buffers: shared hit=4
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.42..2.77 rows=1 width=147) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = av1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.29..8.31 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=28.07..28.08 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Buffers: shared hit=110
                 ->  Nested Loop  (cost=0.71..28.06 rows=2 width=16) (actual time=0.001..0.003 rows=2 loops=10)
                       Buffers: shared hit=110
                       ->  Index Scan using idx_comments_article on comments c1_0  (cost=0.42..11.45 rows=2 width=32) (actual time=0.001..0.001 rows=2 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=50
                       ->  Index Scan using pk_users on users u2_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                             Index Cond: (id = c1_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=60
         SubPlan 2
           ->  Aggregate  (cost=15.13..15.14 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Buffers: shared hit=90
                 ->  Merge Join  (cost=0.71..15.13 rows=2 width=16) (actual time=0.002..0.003 rows=3 loops=10)
                       Merge Cond: (av2_0.user_id = u3_0.id)
                       Buffers: shared hit=90
                       ->  Index Scan using uk_article_views_article_user on article_views av2_0  (cost=0.42..12.32 rows=2 width=32) (actual time=0.001..0.001 rows=3 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Buffers: shared hit=60
                       ->  Index Scan using pk_users on users u3_0  (cost=0.29..471.29 rows=9900 width=16) (actual time=0.001..0.001 rows=3 loops=10)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
 Planning:
   Buffers: shared hit=36
 Planning Time: 0.224 ms
 Execution Time: 0.111 ms
(48 rows)
```

</details>
<details>
<summary>10m - 최근 조회 기사</summary>

```text
                                                                                    QUERY PLAN
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.27..617.71 rows=10 width=206) (actual time=0.031..0.091 rows=10 loops=1)
   Buffers: shared hit=250
   ->  Nested Loop  (cost=1.27..658413.59 rows=10681 width=206) (actual time=0.031..0.090 rows=10 loops=1)
         Buffers: shared hit=250
         ->  Nested Loop  (cost=0.86..86983.62 rows=10681 width=190) (actual time=0.007..0.015 rows=10 loops=1)
               Buffers: shared hit=44
               ->  Index Scan using idx_article_views_user_viewed_id on article_views av1_0  (cost=0.43..17133.12 rows=10900 width=56) (actual time=0.004..0.005 rows=10 loops=1)
                     Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Buffers: shared hit=4
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.43..6.41 rows=1 width=150) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = av1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=10)
               Buffers: shared hit=4
               ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.002 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
         SubPlan 1
           ->  Aggregate  (cost=28.38..28.39 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Buffers: shared hit=130
                 ->  Nested Loop  (cost=0.85..28.37 rows=2 width=16) (actual time=0.002..0.003 rows=2 loops=10)
                       Buffers: shared hit=130
                       ->  Index Scan using idx_comments_article on comments c1_0  (cost=0.43..11.50 rows=2 width=32) (actual time=0.001..0.001 rows=2 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=50
                       ->  Index Scan using pk_users on users u2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                             Index Cond: (id = c1_0.user_id)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=80
         SubPlan 2
           ->  Aggregate  (cost=25.09..25.10 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Buffers: shared hit=72
                 ->  Nested Loop  (cost=0.86..25.08 rows=2 width=16) (actual time=0.002..0.003 rows=3 loops=10)
                       Buffers: shared hit=72
                       ->  Index Scan using uk_article_views_article_user on article_views av2_0  (cost=0.43..12.39 rows=2 width=32) (actual time=0.001..0.002 rows=3 loops=10)
                             Index Cond: (article_id = a1_0.id)
                             Buffers: shared hit=60
                       ->  Memoize  (cost=0.43..8.45 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=30)
                             Cache Key: av2_0.user_id
                             Cache Mode: logical
                             Hits: 27  Misses: 3  Evictions: 0  Overflows: 0  Memory Usage: 1kB
                             Buffers: shared hit=12
                             ->  Index Scan using pk_users on users u3_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=3)
                                   Index Cond: (id = av2_0.user_id)
                                   Filter: (deleted_at IS NULL)
                                   Buffers: shared hit=12
 Planning:
   Buffers: shared hit=40
 Planning Time: 0.217 ms
 Execution Time: 0.113 ms
(53 rows)
```

</details>
