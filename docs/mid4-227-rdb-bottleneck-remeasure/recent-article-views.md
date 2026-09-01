# 최근 조회 기사 실행계획

이 조회는 사용자가 최근에 본 기사 10건과 각 기사의 활성 댓글 수·조회 수를 가져온다. 변경 전에는 댓글 작성자와 기사를 본 사용자가 삭제되었는지 반복해서 확인했다. 변경 후에는 댓글과 기사 조회 데이터의 `visibility_status`를 바로 확인한다.

## 한눈에 보기

- 연결된 조회와 댓글이 많은 조건에서는 실행 시간이 `90.610 ms`에서 `15.484 ms`로 82.9% 줄었다.
- 삭제 기사 조회가 많이 쌓인 조건에서는 `12.476 ms`에서 `3.053 ms`로 75.5% 줄었다.
- 변경 후 가장 큰 병목은 기사 10건의 조회 약 100,030건을 매번 다시 세는 과정이다.
- 활성 데이터만 담는 인덱스를 사용하면 테이블 본문을 다시 읽는 비용은 줄일 수 있지만, 계산해야 하는 행 수 자체는 줄지 않는다.

## 실행 SQL

- before: [before/recent-article-views.sql](../../scripts/performance/activity-history/mid4-227/before/recent-article-views.sql)
- after: [after/recent-article-views.sql](../../scripts/performance/activity-history/mid4-227/after/recent-article-views.sql)

before (`44dcf82`):

```sql
SELECT av.id, u.id AS viewed_by, av.viewed_at,
       a.id AS article_id, a.source, a.link, a.title, a.date, a.summary,
       (SELECT count(c.id)
          FROM comments c
          JOIN users comment_user ON comment_user.id = c.user_id
         WHERE c.article_id = a.id
           AND c.deleted_at IS NULL
           AND comment_user.deleted_at IS NULL) AS article_comment_count,
       (SELECT count(av2.id)
          FROM article_views av2
          JOIN users viewed_user ON viewed_user.id = av2.user_id
         WHERE av2.article_id = a.id
           AND viewed_user.deleted_at IS NULL) AS article_view_count
  FROM article_views av
  JOIN articles a ON a.id = av.article_id
  JOIN users u ON u.id = av.user_id
 WHERE u.id = '00000001-0000-4000-8000-000000000001'
   AND a.deleted_at IS NULL
   AND u.deleted_at IS NULL
 ORDER BY av.viewed_at DESC, av.id DESC
 FETCH FIRST 10 ROWS ONLY;
```

after (`9c195bd`):

```sql
SELECT av.id, av.user_id AS viewed_by, av.viewed_at,
       a.id AS article_id, a.source, a.link, a.title, a.date, a.summary,
       (SELECT count(c.id)
          FROM comments c
         WHERE c.article_id = a.id
           AND c.visibility_status = 'ACTIVE') AS article_comment_count,
       (SELECT count(av2.id)
          FROM article_views av2
         WHERE av2.article_id = a.id
           AND av2.visibility_status = 'ACTIVE') AS article_view_count
  FROM article_views av
  JOIN articles a ON a.id = av.article_id
 WHERE av.user_id = '00000001-0000-4000-8000-000000000001'
   AND av.visibility_status = 'ACTIVE'
 ORDER BY av.viewed_at DESC, av.id DESC
 FETCH FIRST 10 ROWS ONLY;
```

## 반복 측정

같은 데이터와 DB 설정에서 변경 전후 SQL을 비교했다. 각 SQL을 세 번 미리 실행한 다음, 실제 속도를 확인하는 일반 SELECT와 처리 과정을 분석하는 EXPLAIN을 각각 다섯 번 측정했다.

표의 EXPLAIN 값과 아래 실행계획 원문은 같은 조건에서 별도로 측정했다. 실행할 때마다 작은 차이가 생길 수 있으므로 원문의 `Execution Time`은 표의 중앙값과 다를 수 있다. 단위는 ms다.

| 데이터 조건 | 구분 | 일반 SELECT 5회 | 중앙값 | EXPLAIN 5회 | 중앙값 |
| --- | --- | --- | ---: | --- | ---: |
| fanout | before | 124.020, 89.694, 89.607, 90.610, 101.675 | 90.610 | 103.382, 101.173, 101.238, 101.670, 102.467 | 101.670 |
| fanout | after | 15.852, 15.484, 18.750, 15.255, 15.472 | 15.484 | 18.808, 18.458, 18.818, 18.897, 18.699 | 18.808 |
| exclusion | before | 12.476, 12.526, 12.261, 12.328, 12.780 | 12.476 | 11.343, 11.421, 11.321, 11.622, 11.801 | 11.421 |
| exclusion | after | 3.059, 3.053, 2.979, 3.037, 3.861 | 3.053 | 1.396, 1.429, 1.389, 1.571, 1.526 | 1.429 |

일반 SELECT 중앙값은 연결된 데이터가 많은 조건에서 82.9%, 삭제 데이터가 많은 조건에서 75.5% 줄었다.

## 동일 조건 실행계획 원문

아래 코드 블록은 PostgreSQL이 출력한 내용을 그대로 기록한 것이다. 처음 읽을 때는 다음 항목을 확인하면 된다.

- `actual time`: 실제 처리 시간
- `rows`: 한 번에 처리한 행 수
- `loops`: 같은 단계가 반복된 횟수
- `Rows Removed by Filter`: 데이터를 읽은 뒤 조건에 맞지 않아 제외한 행 수
- `Buffers`: 읽은 데이터 블록 수. `shared hit`는 메모리, `read`는 디스크에서 읽었다는 뜻이다.

### fanout before

```text
Limit  (cost=1.27..1033.91 rows=10 width=206) (actual time=10.018..95.315 rows=10 loops=1)
  Output: av.id, u.id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
  Buffers: shared hit=442864
  ->  Nested Loop  (cost=1.27..1265494.23 rows=12255 width=206) (actual time=10.017..95.309 rows=10 loops=1)
        Output: av.id, u.id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
        Buffers: shared hit=442864
        ->  Nested Loop  (cost=0.86..97855.99 rows=12255 width=190) (actual time=0.013..0.065 rows=10 loops=1)
              Output: av.id, av.viewed_at, av.user_id, a.id, a.source, a.link, a.title, a.date, a.summary
              Inner Unique: true
              Buffers: shared hit=44
              ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..20040.16 rows=12503 width=56) (actual time=0.007..0.015 rows=10 loops=1)
                    Output: av.id, av.viewed_at, av.article_id, av.user_id
                    Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Buffers: shared hit=4
              ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.22 rows=1 width=150) (actual time=0.003..0.003 rows=1 loops=10)
                    Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
                    Index Cond: (a.id = av.article_id)
                    Filter: (a.deleted_at IS NULL)
                    Buffers: shared hit=40
        ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
              Output: u.id
              Buffers: shared hit=4
              ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=16) (actual time=0.004..0.005 rows=1 loops=1)
                    Output: u.id
                    Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (u.deleted_at IS NULL)
                    Buffers: shared hit=4
        SubPlan 1
          ->  Aggregate  (cost=28.60..28.61 rows=1 width=8) (actual time=0.868..0.868 rows=1 loops=10)
                Output: count(c.id)
                Buffers: shared hit=40312
                ->  Nested Loop  (cost=0.85..28.59 rows=2 width=16) (actual time=0.009..0.832 rows=1002 loops=10)
                      Output: c.id
                      Inner Unique: true
                      Buffers: shared hit=40312
                      ->  Index Scan using idx_comments_article on public.comments c  (cost=0.43..11.72 rows=2 width=32) (actual time=0.007..0.117 rows=1002 loops=10)
                            Output: c.id, c.user_id
                            Index Cond: (c.article_id = a.id)
                            Filter: (c.deleted_at IS NULL)
                            Buffers: shared hit=232
                      ->  Index Scan using pk_users on public.users comment_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10020)
                            Output: comment_user.id, comment_user.email, comment_user.nickname, comment_user.password, comment_user.created_at, comment_user.updated_at, comment_user.deleted_at
                            Index Cond: (comment_user.id = c.user_id)
                            Filter: (comment_user.deleted_at IS NULL)
                            Buffers: shared hit=40080
        SubPlan 2
          ->  Aggregate  (cost=66.65..66.66 rows=1 width=8) (actual time=8.650..8.650 rows=1 loops=10)
                Output: count(av2.id)
                Buffers: shared hit=402504
                ->  Nested Loop  (cost=0.85..66.63 rows=5 width=16) (actual time=0.007..8.317 rows=10003 loops=10)
                      Output: av2.id
                      Inner Unique: true
                      Buffers: shared hit=402504
                      ->  Index Scan using uk_article_views_article_user on public.article_views av2  (cost=0.43..24.46 rows=5 width=32) (actual time=0.005..1.086 rows=10003 loops=10)
                            Output: av2.id, av2.user_id
                            Index Cond: (av2.article_id = a.id)
                            Buffers: shared hit=2384
                      ->  Index Scan using pk_users on public.users viewed_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=100030)
                            Output: viewed_user.id, viewed_user.email, viewed_user.nickname, viewed_user.password, viewed_user.created_at, viewed_user.updated_at, viewed_user.deleted_at
                            Index Cond: (viewed_user.id = av2.user_id)
                            Filter: (viewed_user.deleted_at IS NULL)
                            Buffers: shared hit=400120
Planning:
  Buffers: shared hit=432
Planning Time: 0.831 ms
Execution Time: 95.408 ms
```

### fanout after

```text
Limit  (cost=0.86..442.10 rows=10 width=206) (actual time=1.854..17.181 rows=10 loops=1)
  Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
  Buffers: shared hit=2456
  ->  Nested Loop  (cost=0.86..541096.99 rows=12263 width=206) (actual time=1.853..17.178 rows=10 loops=1)
        Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
        Inner Unique: true
        Buffers: shared hit=2456
        ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..20071.41 rows=12263 width=56) (actual time=0.018..0.022 rows=10 loops=1)
              Output: av.id, av.user_id, av.viewed_at, av.article_id
              Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: ((av.visibility_status)::text = 'ACTIVE'::text)
              Buffers: shared hit=4
        ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.25 rows=1 width=150) (actual time=0.003..0.003 rows=1 loops=10)
              Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
              Index Cond: (a.id = av.article_id)
              Buffers: shared hit=40
        SubPlan 1
          ->  Aggregate  (cost=11.73..11.74 rows=1 width=8) (actual time=0.175..0.175 rows=1 loops=10)
                Output: count(c.id)
                Buffers: shared hit=232
                ->  Index Scan using idx_comments_article on public.comments c  (cost=0.43..11.73 rows=2 width=16) (actual time=0.006..0.137 rows=1002 loops=10)
                      Output: c.id
                      Index Cond: (c.article_id = a.id)
                      Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=232
        SubPlan 2
          ->  Aggregate  (cost=24.48..24.49 rows=1 width=8) (actual time=1.534..1.534 rows=1 loops=10)
                Output: count(av2.id)
                Buffers: shared hit=2180
                ->  Index Scan using idx_article_views_article_viewed on public.article_views av2  (cost=0.43..24.47 rows=5 width=16) (actual time=0.005..1.199 rows=10003 loops=10)
                      Output: av2.id
                      Index Cond: (av2.article_id = a.id)
                      Filter: ((av2.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=2180
Planning:
  Buffers: shared hit=361
Planning Time: 0.824 ms
Execution Time: 17.233 ms
```

### exclusion before

```text
Limit  (cost=1.28..609.16 rows=10 width=206) (actual time=12.575..12.655 rows=10 loops=1)
  Output: av.id, u.id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
  Buffers: shared hit=40531
  ->  Nested Loop  (cost=1.28..1230775.39 rows=20247 width=206) (actual time=12.572..12.652 rows=10 loops=1)
        Output: av.id, u.id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
        Buffers: shared hit=40531
        ->  Nested Loop  (cost=0.86..141685.04 rows=20247 width=190) (actual time=12.465..12.478 rows=10 loops=1)
              Output: av.id, av.viewed_at, av.user_id, a.id, a.source, a.link, a.title, a.date, a.summary
              Inner Unique: true
              Buffers: shared hit=40305
              ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..32170.83 rows=20769 width=56) (actual time=0.033..1.738 rows=10010 loops=1)
                    Output: av.id, av.viewed_at, av.article_id, av.user_id
                    Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Buffers: shared hit=265
              ->  Index Scan using pk_articles on public.articles a  (cost=0.43..5.27 rows=1 width=150) (actual time=0.001..0.001 rows=0 loops=10010)
                    Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
                    Index Cond: (a.id = av.article_id)
                    Filter: (a.deleted_at IS NULL)
                    Rows Removed by Filter: 1
                    Buffers: shared hit=40040
        ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=10)
              Output: u.id
              Buffers: shared hit=4
              ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=16) (actual time=0.011..0.011 rows=1 loops=1)
                    Output: u.id
                    Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (u.deleted_at IS NULL)
                    Buffers: shared hit=4
        SubPlan 1
          ->  Aggregate  (cost=28.68..28.69 rows=1 width=8) (actual time=0.009..0.009 rows=1 loops=10)
                Output: count(c.id)
                Buffers: shared hit=150
                ->  Nested Loop  (cost=0.85..28.67 rows=1 width=16) (actual time=0.005..0.008 rows=2 loops=10)
                      Output: c.id
                      Inner Unique: true
                      Buffers: shared hit=150
                      ->  Index Scan using idx_comments_article on public.comments c  (cost=0.43..11.80 rows=2 width=32) (actual time=0.003..0.005 rows=2 loops=10)
                            Output: c.id, c.user_id
                            Index Cond: (c.article_id = a.id)
                            Filter: (c.deleted_at IS NULL)
                            Rows Removed by Filter: 2
                            Buffers: shared hit=70
                      ->  Index Scan using pk_users on public.users comment_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                            Output: comment_user.id, comment_user.email, comment_user.nickname, comment_user.password, comment_user.created_at, comment_user.updated_at, comment_user.deleted_at
                            Index Cond: (comment_user.id = c.user_id)
                            Filter: (comment_user.deleted_at IS NULL)
                            Buffers: shared hit=80
        SubPlan 2
          ->  Aggregate  (cost=25.08..25.09 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                Output: count(av2.id)
                Buffers: shared hit=72
                ->  Nested Loop  (cost=0.86..25.08 rows=1 width=16) (actual time=0.003..0.005 rows=3 loops=10)
                      Output: av2.id
                      Inner Unique: true
                      Buffers: shared hit=72
                      ->  Index Scan using uk_article_views_article_user on public.article_views av2  (cost=0.43..12.38 rows=2 width=32) (actual time=0.002..0.003 rows=3 loops=10)
                            Output: av2.id, av2.user_id
                            Index Cond: (av2.article_id = a.id)
                            Buffers: shared hit=60
                      ->  Memoize  (cost=0.43..8.45 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=30)
                            Output: viewed_user.id
                            Cache Key: av2.user_id
                            Cache Mode: logical
                            Hits: 27  Misses: 3  Evictions: 0  Overflows: 0  Memory Usage: 1kB
                            Buffers: shared hit=12
                            ->  Index Scan using pk_users on public.users viewed_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=3)
                                  Output: viewed_user.id
                                  Index Cond: (viewed_user.id = av2.user_id)
                                  Filter: (viewed_user.deleted_at IS NULL)
                                  Buffers: shared hit=12
Planning:
  Buffers: shared hit=424
Planning Time: 1.980 ms
Execution Time: 12.795 ms
```

### exclusion after

```text
Limit  (cost=0.86..312.17 rows=10 width=206) (actual time=1.502..1.555 rows=10 loops=1)
  Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
  Buffers: shared hit=435
  ->  Nested Loop  (cost=0.86..630618.19 rows=20257 width=206) (actual time=1.501..1.553 rows=10 loops=1)
        Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
        Inner Unique: true
        Buffers: shared hit=435
        ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..32222.76 rows=20257 width=56) (actual time=1.439..1.441 rows=10 loops=1)
              Output: av.id, av.user_id, av.viewed_at, av.article_id
              Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: ((av.visibility_status)::text = 'ACTIVE'::text)
              Rows Removed by Filter: 10000
              Buffers: shared hit=265
        ->  Index Scan using pk_articles on public.articles a  (cost=0.43..5.32 rows=1 width=150) (actual time=0.002..0.002 rows=1 loops=10)
              Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
              Index Cond: (a.id = av.article_id)
              Buffers: shared hit=40
        SubPlan 1
          ->  Aggregate  (cost=11.81..11.82 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                Output: count(c.id)
                Buffers: shared hit=70
                ->  Index Scan using idx_comments_article on public.comments c  (cost=0.43..11.80 rows=2 width=16) (actual time=0.003..0.005 rows=2 loops=10)
                      Output: c.id
                      Index Cond: (c.article_id = a.id)
                      Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                      Rows Removed by Filter: 2
                      Buffers: shared hit=70
        SubPlan 2
          ->  Aggregate  (cost=12.39..12.40 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                Output: count(av2.id)
                Buffers: shared hit=60
                ->  Index Scan using idx_article_views_article_viewed on public.article_views av2  (cost=0.43..12.38 rows=2 width=16) (actual time=0.001..0.002 rows=3 loops=10)
                      Output: av2.id
                      Index Cond: (av2.article_id = a.id)
                      Filter: ((av2.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=60
Planning:
  Buffers: shared hit=361
Planning Time: 0.854 ms
Execution Time: 1.603 ms
```

## 기술 근거와 개선 방향

### 측정에서 확인한 내용

- 변경 전에는 기사를 조회한 사용자를 기본 키(PK)로 100,030회 확인했다. 이 반복 작업을 포함한 조회 수 계산 구간에 약 92.13 ms가 걸렸다.
- 변경 후에는 기사 조회 데이터의 상태를 바로 확인해 사용자 반복 조회를 제거했다.
- 삭제 기사 조회가 많은 조건에서도 비활성 조회 10,000건을 먼저 제외한다. 따라서 실제 결과 10건에 대해서만 기사 정보를 읽는다.
- 변경 후에도 기사마다 활성 조회 수를 다시 계산한다. 기사 한 건당 10,003건을 처리하고(`rows=10003`) 이 작업을 10번 반복한다(`loops=10`).
- 총 약 100,030건을 처리하는 13.82 ms 구간이 현재 가장 큰 병목이다.

### 후속 개선 후보

- 기사별 조회 수와 댓글 수에 각각 `(article_id) WHERE visibility_status = 'ACTIVE'` 부분 인덱스를 추가한다.
- 집계를 `COUNT(*)`로 바꾸면 테이블 본문(heap)을 다시 읽는 비용을 줄이고 인덱스만 읽는 방식(`Index Only Scan`)을 기대할 수 있다.
- 모든 데이터가 `ACTIVE`라면 조회 약 100,030건과 댓글 약 10,020건을 세는 작업은 그대로 남는다. 연결 데이터 증가 문제를 인덱스만으로 해결할 수는 없다.
- 사용자의 최근 조회 목록에는 `idx_article_views_user_viewed_id`와 같은 키의 활성 데이터 전용 부분 인덱스를 추가한다. 필요한 활성 조회 10건을 찾으면 바로 조회를 끝낼 수 있다.
- 기존 `idx_article_views_article_viewed(article_id, viewed_at DESC)`에는 조회 수 계산에 필요하지 않은 `viewed_at`이 들어 있다. 따라서 기존 인덱스를 바꾸지 않고 집계 전용 인덱스를 새 이름으로 만든다.
- `idx_comments_article`과 `idx_article_views_user_viewed_id`도 기존 DB 변경 이력 파일은 수정하지 않는다. 새로운 변경 이력 파일(migration)에 부분 인덱스를 추가한다.
- 새 인덱스의 실행계획을 비교하고 비활성 상태까지 조회하는 다른 기능이 없는지 확인한 뒤 기존 인덱스 제거 여부를 결정한다.
