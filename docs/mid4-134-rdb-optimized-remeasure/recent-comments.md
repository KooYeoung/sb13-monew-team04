# 최근 작성 댓글 재측정

> [MID4-134 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 한눈에 보기

- 사용자별 최근 댓글 인덱스를 적용하자 가장 큰 테스트 데이터의 실행 시간 가운데 값이 `82.747 ms`에서 `0.476 ms`로 99.42% 줄었다.
- DB가 전체 댓글을 넓게 읽고 정렬하는 대신 대상 사용자의 최신 댓글 10건부터 찾게 됐다.
- 댓글별 좋아요 수를 세는 하위 조회는 남아 있지만 현재 결과는 모두 1ms 미만이어서 이번 작업에서는 분리하지 않았다.

아래부터는 이 결론의 측정값과 실행계획을 기록한다.

MID4-133 인덱스 적용 후 `comments.user_id` 접근과 최신순 정렬이 `idx_comments_user_created_id`로 처리된다. 10m seed scale에서도 median은 `0.476 ms`로 baseline `82.747 ms` 대비 `99.42%` 감소했고, scale 증가에 따른 선형 악화가 보이지 않는다.

또한 이 조회는 최신 작성 댓글 10건만 반환하므로 댓글 좋아요 수 count subquery도 최종 반환 row 기준으로 최대 10회 반복된다. optimized 측정에서 100k, 1m, 10m scale의 median이 `0.431 ms`, `0.423 ms`, `0.476 ms`로 모두 1ms 미만이고 scale 증가에 따른 뚜렷한 악화가 보이지 않는다. 따라서 count subquery를 별도 쿼리로 분리할 근거는 현재 수치상 부족하다. 별도 count 쿼리로 나누면 SQL 자체는 단순해질 수 있지만, 추가 round-trip과 애플리케이션 조립 비용이 생기므로 MID4-134에서는 현재 쿼리 구조를 유지하고 인덱스 적용 결과만 기록한다.

## 어떤 인덱스를 적용했나

| index | columns | 목적 |
| --- | --- | --- |
| `idx_comments_user_created_id` | `comments(user_id, created_at DESC, id DESC)` | 사용자 필터와 최신순 정렬 |

## 측정 기준

- raw: [raw/rerun-132-method](raw/rerun-132-method)
- SQL template: [raw/rerun-132-method/sql-template.sql](raw/rerun-132-method/sql-template.sql)
- 측정 방식: seed 후 ANALYZE, 조회별 3회 warm-up, EXPLAIN (ANALYZE, BUFFERS), 동일 SQL 5회 반복 실행 후 median 기록
- 측정 SQL: 기존 Hibernate SQL 기준이며, userId binding만 00000001-0000-4000-8000-000000000001 literal로 치환

## 실행 시간

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `7.736` ms | `0.095` ms | `8.953`, `8.983`, `11.553`, `9.165`, `8.712` ms | `0.500`, `0.431`, `0.391`, `0.385`, `0.547` ms | `8.983` ms | `0.431` ms |
| `1m` | `12.836` ms | `0.088` ms | `14.151`, `13.587`, `13.720`, `17.696`, `14.005` ms | `0.415`, `0.423`, `0.389`, `0.424`, `0.468` ms | `14.005` ms | `0.423` ms |
| `10m` | `88.314` ms | `0.105` ms | `80.545`, `82.747`, `95.963`, `93.401`, `78.940` ms | `0.490`, `0.476`, `0.519`, `0.391`, `0.389` ms | `82.747` ms | `0.476` ms |

## 실행 시간 가운데 값 비교

| seed scale | baseline median | optimized median | delta | change |
| --- | ---: | ---: | ---: | ---: |
| `100k` | `8.983` ms | `0.431` ms | `-8.552` ms | `-95.20`% |
| `1m` | `14.005` ms | `0.423` ms | `-13.582` ms | `-96.98`% |
| `10m` | `82.747` ms | `0.476` ms | `-82.271` ms | `-99.42`% |

## DB가 데이터를 찾은 방법

| seed scale | 주요 접근 경로 | Execution Time |
| --- | --- | ---: |
| `100k` | `idx_comments_user_created_id`, `pk_articles`, `pk_users`, `uk_comment_likes_comment_liked_by` | `0.095` ms |
| `1m` | `idx_comments_user_created_id`, `pk_articles`, `pk_users`, `uk_comment_likes_comment_liked_by` | `0.088` ms |
| `10m` | `idx_comments_user_created_id`, `pk_articles`, `pk_users`, `uk_comment_likes_comment_liked_by` | `0.105` ms |

## 실행계획 원문

<details>
<summary>100k - 최근 작성 댓글</summary>

```text
                                                                                    QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.98..218.46 rows=10 width=132) (actual time=0.033..0.068 rows=10 loops=1)
   Buffers: shared hit=97
   ->  Nested Loop  (cost=0.98..20466.20 rows=941 width=132) (actual time=0.032..0.067 rows=10 loops=1)
         Buffers: shared hit=97
         ->  Nested Loop  (cost=0.70..4800.91 rows=941 width=111) (actual time=0.013..0.024 rows=10 loops=1)
               Buffers: shared hit=34
               ->  Index Scan using idx_comments_user_created_id on comments c1_0  (cost=0.41..2208.42 rows=959 width=86) (actual time=0.005..0.007 rows=10 loops=1)
                     Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.29..2.70 rows=1 width=41) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=30
         ->  Materialize  (cost=0.28..8.30 rows=1 width=29) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=29) (actual time=0.003..0.003 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=16.62..16.63 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Buffers: shared hit=60
                 ->  Nested Loop  (cost=0.56..16.61 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=10)
                       Buffers: shared hit=60
                       ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl1_0  (cost=0.29..8.30 rows=1 width=32) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (comment_id = c1_0.id)
                             Buffers: shared hit=30
                       ->  Index Scan using pk_users on users lb1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (id = cl1_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
 Planning:
   Buffers: shared hit=24
 Planning Time: 0.200 ms
 Execution Time: 0.095 ms
(36 rows)
```

</details>
<details>
<summary>1m - 최근 작성 댓글</summary>

```text
                                                                                    QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.13..220.57 rows=10 width=135) (actual time=0.030..0.061 rows=10 loops=1)
   Buffers: shared hit=117
   ->  Nested Loop  (cost=1.13..210250.19 rows=9581 width=135) (actual time=0.029..0.060 rows=10 loops=1)
         Buffers: shared hit=117
         ->  Nested Loop  (cost=0.84..49576.95 rows=9581 width=113) (actual time=0.013..0.023 rows=10 loops=1)
               Buffers: shared hit=44
               ->  Index Scan using idx_comments_user_created_id on comments c1_0  (cost=0.42..22210.51 rows=9761 width=87) (actual time=0.009..0.010 rows=10 loops=1)
                     Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.42..2.80 rows=1 width=42) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.29..8.31 rows=1 width=30) (actual time=0.001..0.001 rows=1 loops=10)
               Buffers: shared hit=3
               ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=30) (actual time=0.004..0.005 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=3
         SubPlan 1
           ->  Aggregate  (cost=16.75..16.76 rows=1 width=8) (actual time=0.002..0.002 rows=1 loops=10)
                 Buffers: shared hit=70
                 ->  Nested Loop  (cost=0.71..16.74 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=10)
                       Buffers: shared hit=70
                       ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl1_0  (cost=0.42..8.44 rows=1 width=32) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (comment_id = c1_0.id)
                             Buffers: shared hit=40
                       ->  Index Scan using pk_users on users lb1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (id = cl1_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=30
 Planning:
   Buffers: shared hit=30
 Planning Time: 0.218 ms
 Execution Time: 0.088 ms
(36 rows)
```

</details>
<details>
<summary>10m - 최근 작성 댓글</summary>

```text
                                                                                    QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.27..279.82 rows=10 width=138) (actual time=0.047..0.077 rows=10 loops=1)
   Buffers: shared hit=128
   ->  Nested Loop  (cost=1.27..228133.02 rows=8190 width=138) (actual time=0.046..0.076 rows=10 loops=1)
         Buffers: shared hit=128
         ->  Nested Loop  (cost=0.86..89650.83 rows=8190 width=115) (actual time=0.012..0.021 rows=10 loops=1)
               Buffers: shared hit=44
               ->  Index Scan using idx_comments_user_created_id on comments c1_0  (cost=0.43..33311.52 rows=8358 width=88) (actual time=0.008..0.010 rows=10 loops=1)
                     Index Cond: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
               ->  Index Scan using pk_articles on articles a1_0  (cost=0.43..6.74 rows=1 width=43) (actual time=0.001..0.001 rows=1 loops=10)
                     Index Cond: (id = c1_0.article_id)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=40
         ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.002..0.003 rows=1 loops=10)
               Buffers: shared hit=4
               ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=31) (actual time=0.011..0.012 rows=1 loops=1)
                     Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=4
         SubPlan 1
           ->  Aggregate  (cost=16.89..16.90 rows=1 width=8) (actual time=0.002..0.002 rows=1 loops=10)
                 Buffers: shared hit=80
                 ->  Nested Loop  (cost=0.85..16.88 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=10)
                       Buffers: shared hit=80
                       ->  Index Scan using uk_comment_likes_comment_liked_by on comment_likes cl1_0  (cost=0.43..8.45 rows=1 width=32) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (comment_id = c1_0.id)
                             Buffers: shared hit=40
                       ->  Index Scan using pk_users on users lb1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
                             Index Cond: (id = cl1_0.liked_by)
                             Filter: (deleted_at IS NULL)
                             Buffers: shared hit=40
 Planning:
   Buffers: shared hit=32
 Planning Time: 0.197 ms
 Execution Time: 0.105 ms
(36 rows)
```

</details>
