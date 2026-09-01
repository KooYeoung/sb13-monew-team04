# 최근 좋아요한 댓글 실행계획

> [MID4-227 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 조회는 사용자가 최근에 좋아요를 누른 활성 댓글 10건과 각 댓글의 활성 좋아요 수를 가져온다. 변경 전에는 좋아요와 댓글에 연결된 사용자가 삭제되었는지 반복해서 확인했다. 변경 후에는 각 활동 데이터의 `visibility_status`를 바로 확인한다.

## 한눈에 보기

- 연결된 데이터가 많은 조건에서는 실행 시간이 `2.533 ms`에서 `2.335 ms`로 7.8% 줄었다. 이 조회에 직접 연결된 좋아요 수가 많지 않아 차이가 작았다.
- 삭제 좋아요가 많이 쌓인 조건에서는 `10.783 ms`에서 `3.476 ms`로 67.8% 줄었다.
- 변경 후에도 최근 활성 좋아요 10건을 찾기 위해 비활성 좋아요 10,000건을 먼저 읽고 제외한다.
- 활성 좋아요만 담는 최근순 인덱스가 가장 우선적인 다음 개선 후보다. 댓글별 좋아요 수 계산용 인덱스는 현재 실행계획에서는 우선순위가 낮다.

## 실행 SQL

- before: [before/recent-comment-likes.sql](../../scripts/performance/activity-history/mid4-227/before/recent-comment-likes.sql)
- after: [after/recent-comment-likes.sql](../../scripts/performance/activity-history/mid4-227/after/recent-comment-likes.sql)

before (`44dcf82`):

```sql
SELECT cl.id, cl.created_at, c.id AS comment_id,
       a.id AS article_id, a.title AS article_title,
       comment_user.id AS comment_user_id,
       comment_user.nickname AS comment_user_nickname,
       c.content AS comment_content,
       (SELECT count(cl2.id)
          FROM comment_likes cl2
          JOIN comments c2 ON c2.id = cl2.comment_id
          JOIN users liked_user ON liked_user.id = cl2.liked_by
         WHERE c2.id = c.id
           AND liked_user.deleted_at IS NULL) AS comment_like_count,
       c.created_at AS comment_created_at
  FROM comment_likes cl
  JOIN comments c ON c.id = cl.comment_id
  JOIN users liked_by ON liked_by.id = cl.liked_by
  JOIN users comment_user ON comment_user.id = c.user_id
  JOIN articles a ON a.id = c.article_id
 WHERE liked_by.id = '00000001-0000-4000-8000-000000000001'
   AND liked_by.deleted_at IS NULL
   AND comment_user.deleted_at IS NULL
   AND a.deleted_at IS NULL
   AND c.deleted_at IS NULL
 ORDER BY cl.created_at DESC, cl.id DESC
 FETCH FIRST 10 ROWS ONLY;
```

after (`9c195bd`):

```sql
SELECT cl.id, cl.created_at, c.id AS comment_id,
       a.id AS article_id, a.title AS article_title,
       u.id AS comment_user_id, u.nickname AS comment_user_nickname,
       c.content AS comment_content,
       (SELECT count(cl2.id)
          FROM comment_likes cl2
         WHERE cl2.comment_id = c.id
           AND cl2.visibility_status = 'ACTIVE') AS comment_like_count,
       c.created_at AS comment_created_at
  FROM comment_likes cl
  JOIN comments c ON c.id = cl.comment_id
  JOIN users u ON u.id = c.user_id
  JOIN articles a ON a.id = c.article_id
 WHERE cl.liked_by = '00000001-0000-4000-8000-000000000001'
   AND cl.visibility_status = 'ACTIVE'
 ORDER BY cl.created_at DESC, cl.id DESC
 FETCH FIRST 10 ROWS ONLY;
```

## 반복 측정

같은 데이터와 DB 설정에서 변경 전후 SQL을 비교했다. 각 SQL을 세 번 미리 실행한 다음, 실제 속도를 확인하는 일반 SELECT와 처리 과정을 분석하는 EXPLAIN을 각각 다섯 번 측정했다.

표의 EXPLAIN 값과 아래 실행계획 원문은 같은 조건에서 별도로 측정했다. 실행할 때마다 작은 차이가 생길 수 있으므로 원문의 `Execution Time`은 표의 중앙값과 다를 수 있다. 단위는 ms다.

| 데이터 조건 | 구분 | 일반 SELECT 5회 | 중앙값 | EXPLAIN 5회 | 중앙값 |
| --- | --- | --- | ---: | --- | ---: |
| fanout | before | 2.453, 2.844, 2.582, 2.437, 2.533 | 2.533 | 0.265, 0.272, 0.262, 0.296, 0.263 | 0.265 |
| fanout | after | 3.327, 2.303, 2.196, 2.335, 2.472 | 2.335 | 0.214, 0.204, 0.165, 0.240, 0.173 | 0.204 |
| exclusion | before | 11.124, 10.694, 11.030, 10.608, 10.783 | 10.783 | 9.678, 9.552, 9.770, 9.907, 9.767 | 9.767 |
| exclusion | after | 3.476, 3.595, 3.443, 3.371, 3.513 | 3.476 | 1.972, 1.476, 1.411, 1.318, 1.987 | 1.476 |

연결된 데이터가 많은 조건에서도 대상 사용자의 최근 좋아요에는 데이터가 많이 연결되지 않아 일반 SELECT 중앙값이 7.8% 줄어드는 데 그쳤다. 삭제 데이터가 많은 조건에서는 67.8% 줄었다.

## 동일 조건 실행계획 원문

아래 코드 블록은 PostgreSQL이 출력한 내용을 그대로 기록한 것이다. 처음 읽을 때는 다음 항목을 확인하면 된다.

- `actual time`: 실제 처리 시간
- `rows`: 한 번에 처리한 행 수
- `loops`: 같은 단계가 반복된 횟수
- `Rows Removed by Filter`: 데이터를 읽은 뒤 조건에 맞지 않아 제외한 행 수
- `Buffers`: 읽은 데이터 블록 수. `shared hit`는 메모리, `read`는 디스크에서 읽었다는 뜻이다.

### fanout before

```text
Limit  (cost=2.12..384.39 rows=10 width=162) (actual time=0.099..0.176 rows=10 loops=1)
  Output: cl.id, cl.created_at, c.id, a.id, a.title, comment_user.id, comment_user.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=319
  ->  Nested Loop  (cost=2.12..301150.05 rows=7878 width=162) (actual time=0.098..0.174 rows=10 loops=1)
        Output: cl.id, cl.created_at, c.id, a.id, a.title, comment_user.id, comment_user.nickname, c.content, (SubPlan 1), c.created_at
        Buffers: shared hit=319
        ->  Nested Loop  (cost=1.71..101314.87 rows=7878 width=170) (actual time=0.057..0.087 rows=10 loops=1)
              Output: cl.id, cl.created_at, cl.liked_by, c.id, c.content, c.created_at, comment_user.id, comment_user.nickname, a.id, a.title
              Inner Unique: true
              Buffers: shared hit=124
              ->  Nested Loop  (cost=1.29..97838.80 rows=7954 width=155) (actual time=0.048..0.068 rows=10 loops=1)
                    Output: cl.id, cl.created_at, cl.liked_by, c.id, c.content, c.created_at, c.user_id, a.id, a.title
                    Inner Unique: true
                    Buffers: shared hit=84
                    ->  Nested Loop  (cost=0.86..93719.97 rows=8115 width=128) (actual time=0.019..0.030 rows=10 loops=1)
                          Output: cl.id, cl.created_at, cl.liked_by, c.id, c.content, c.created_at, c.user_id, c.article_id
                          Inner Unique: true
                          Buffers: shared hit=44
                          ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..30993.90 rows=8532 width=56) (actual time=0.008..0.010 rows=10 loops=1)
                                Output: cl.id, cl.created_at, cl.comment_id, cl.liked_by
                                Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                Buffers: shared hit=4
                          ->  Index Scan using pk_comments on public.comments c  (cost=0.43..7.35 rows=1 width=88) (actual time=0.002..0.002 rows=1 loops=10)
                                Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                                Index Cond: (c.id = cl.comment_id)
                                Filter: (c.deleted_at IS NULL)
                                Buffers: shared hit=40
                    ->  Index Scan using pk_articles on public.articles a  (cost=0.43..0.51 rows=1 width=43) (actual time=0.004..0.004 rows=1 loops=10)
                          Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
                          Index Cond: (a.id = c.article_id)
                          Filter: (a.deleted_at IS NULL)
                          Buffers: shared hit=40
              ->  Index Scan using pk_users on public.users comment_user  (cost=0.42..0.44 rows=1 width=31) (actual time=0.002..0.002 rows=1 loops=10)
                    Output: comment_user.id, comment_user.email, comment_user.nickname, comment_user.password, comment_user.created_at, comment_user.updated_at, comment_user.deleted_at
                    Index Cond: (comment_user.id = c.user_id)
                    Filter: (comment_user.deleted_at IS NULL)
                    Buffers: shared hit=40
        ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
              Output: liked_by.id
              Buffers: shared hit=4
              ->  Index Scan using pk_users on public.users liked_by  (cost=0.42..8.44 rows=1 width=16) (actual time=0.005..0.005 rows=1 loops=1)
                    Output: liked_by.id
                    Index Cond: (liked_by.id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (liked_by.deleted_at IS NULL)
                    Buffers: shared hit=4
        SubPlan 1
          ->  Aggregate  (cost=25.34..25.35 rows=1 width=8) (actual time=0.007..0.007 rows=1 loops=10)
                Output: count(cl2.id)
                Buffers: shared hit=191
                ->  Nested Loop  (cost=1.28..25.34 rows=1 width=16) (actual time=0.004..0.006 rows=2 loops=10)
                      Output: cl2.id
                      Inner Unique: true
                      Buffers: shared hit=191
                      ->  Nested Loop  (cost=0.86..16.91 rows=1 width=32) (actual time=0.003..0.004 rows=2 loops=10)
                            Output: cl2.id, cl2.liked_by
                            Buffers: shared hit=111
                            ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl2  (cost=0.43..8.45 rows=1 width=48) (actual time=0.001..0.002 rows=2 loops=10)
                                  Output: cl2.id, cl2.comment_id, cl2.liked_by
                                  Index Cond: (cl2.comment_id = c.id)
                                  Buffers: shared hit=50
                            ->  Index Only Scan using pk_comments on public.comments c2  (cost=0.43..8.45 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                                  Output: c2.id
                                  Index Cond: (c2.id = c.id)
                                  Heap Fetches: 0
                                  Buffers: shared hit=61
                      ->  Index Scan using pk_users on public.users liked_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                            Output: liked_user.id, liked_user.email, liked_user.nickname, liked_user.password, liked_user.created_at, liked_user.updated_at, liked_user.deleted_at
                            Index Cond: (liked_user.id = cl2.liked_by)
                            Filter: (liked_user.deleted_at IS NULL)
                            Buffers: shared hit=80
Planning:
  Buffers: shared hit=430
Planning Time: 1.221 ms
Execution Time: 0.265 ms
```

### fanout after

```text
Limit  (cost=1.70..208.90 rows=10 width=162) (actual time=0.046..0.090 rows=10 loops=1)
  Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=174
  ->  Nested Loop  (cost=1.70..164682.32 rows=7948 width=162) (actual time=0.045..0.088 rows=10 loops=1)
        Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
        Inner Unique: true
        Buffers: shared hit=174
        ->  Nested Loop  (cost=1.28..93412.61 rows=7948 width=127) (actual time=0.023..0.043 rows=10 loops=1)
              Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
              Inner Unique: true
              Buffers: shared hit=84
              ->  Nested Loop  (cost=0.86..89939.96 rows=7948 width=112) (actual time=0.020..0.031 rows=10 loops=1)
                    Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.user_id, c.article_id
                    Inner Unique: true
                    Buffers: shared hit=44
                    ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..31015.23 rows=7948 width=40) (actual time=0.013..0.015 rows=10 loops=1)
                          Output: cl.id, cl.created_at, cl.comment_id
                          Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                          Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                          Buffers: shared hit=4
                    ->  Index Scan using pk_comments on public.comments c  (cost=0.43..7.41 rows=1 width=88) (actual time=0.001..0.001 rows=1 loops=10)
                          Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                          Index Cond: (c.id = cl.comment_id)
                          Buffers: shared hit=40
              ->  Index Scan using pk_users on public.users u  (cost=0.42..0.44 rows=1 width=31) (actual time=0.001..0.001 rows=1 loops=10)
                    Output: u.id, u.email, u.nickname, u.password, u.created_at, u.updated_at, u.deleted_at
                    Index Cond: (u.id = c.user_id)
                    Buffers: shared hit=40
        ->  Index Scan using pk_articles on public.articles a  (cost=0.43..0.50 rows=1 width=43) (actual time=0.001..0.001 rows=1 loops=10)
              Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
              Index Cond: (a.id = c.article_id)
              Buffers: shared hit=40
        SubPlan 1
          ->  Aggregate  (cost=8.45..8.46 rows=1 width=8) (actual time=0.002..0.002 rows=1 loops=10)
                Output: count(cl2.id)
                Buffers: shared hit=50
                ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl2  (cost=0.43..8.45 rows=1 width=16) (actual time=0.001..0.002 rows=2 loops=10)
                      Output: cl2.id
                      Index Cond: (cl2.comment_id = c.id)
                      Filter: ((cl2.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=50
Planning:
  Buffers: shared hit=408
Planning Time: 0.905 ms
Execution Time: 0.147 ms
```

### exclusion before

```text
Limit  (cost=2.13..386.17 rows=10 width=168) (actual time=9.470..9.548 rows=10 loops=1)
  Output: cl.id, cl.created_at, c.id, a.id, a.title, comment_user.id, comment_user.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=40571
  ->  Nested Loop  (cost=2.13..456517.90 rows=11887 width=168) (actual time=9.468..9.545 rows=10 loops=1)
        Output: cl.id, cl.created_at, c.id, a.id, a.title, comment_user.id, comment_user.nickname, c.content, (SubPlan 1), c.created_at
        Buffers: shared hit=40571
        ->  Nested Loop  (cost=1.71..202512.06 rows=11887 width=176) (actual time=9.427..9.457 rows=10 loops=1)
              Output: cl.id, cl.created_at, cl.liked_by, c.id, c.content, c.created_at, comment_user.id, comment_user.nickname, a.id, a.title
              Inner Unique: true
              Buffers: shared hit=40376
              ->  Nested Loop  (cost=1.28..196315.89 rows=12193 width=149) (actual time=9.417..9.437 rows=10 loops=1)
                    Output: cl.id, cl.created_at, cl.liked_by, c.id, c.content, c.created_at, c.article_id, comment_user.id, comment_user.nickname
                    Inner Unique: true
                    Buffers: shared hit=40336
                    ->  Nested Loop  (cost=0.86..188163.80 rows=18487 width=128) (actual time=9.403..9.415 rows=10 loops=1)
                          Output: cl.id, cl.created_at, cl.liked_by, c.id, c.content, c.created_at, c.user_id, c.article_id
                          Inner Unique: true
                          Buffers: shared hit=40296
                          ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..62788.46 rows=19565 width=56) (actual time=0.008..1.201 rows=10010 loops=1)
                                Output: cl.id, cl.created_at, cl.comment_id, cl.liked_by
                                Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                                Buffers: shared hit=256
                          ->  Index Scan using pk_comments on public.comments c  (cost=0.43..6.41 rows=1 width=88) (actual time=0.001..0.001 rows=0 loops=10010)
                                Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                                Index Cond: (c.id = cl.comment_id)
                                Filter: (c.deleted_at IS NULL)
                                Rows Removed by Filter: 1
                                Buffers: shared hit=40040
                    ->  Index Scan using pk_users on public.users comment_user  (cost=0.42..0.44 rows=1 width=37) (actual time=0.002..0.002 rows=1 loops=10)
                          Output: comment_user.id, comment_user.email, comment_user.nickname, comment_user.password, comment_user.created_at, comment_user.updated_at, comment_user.deleted_at
                          Index Cond: (comment_user.id = c.user_id)
                          Filter: (comment_user.deleted_at IS NULL)
                          Buffers: shared hit=40
              ->  Index Scan using pk_articles on public.articles a  (cost=0.43..0.51 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
                    Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
                    Index Cond: (a.id = c.article_id)
                    Filter: (a.deleted_at IS NULL)
                    Buffers: shared hit=40
        ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10)
              Output: liked_by.id
              Buffers: shared hit=4
              ->  Index Scan using pk_users on public.users liked_by  (cost=0.42..8.44 rows=1 width=16) (actual time=0.005..0.005 rows=1 loops=1)
                    Output: liked_by.id
                    Index Cond: (liked_by.id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (liked_by.deleted_at IS NULL)
                    Buffers: shared hit=4
        SubPlan 1
          ->  Aggregate  (cost=21.35..21.36 rows=1 width=8) (actual time=0.007..0.007 rows=1 loops=10)
                Output: count(cl2.id)
                Buffers: shared hit=191
                ->  Nested Loop  (cost=1.28..21.34 rows=1 width=16) (actual time=0.004..0.006 rows=2 loops=10)
                      Output: cl2.id
                      Inner Unique: true
                      Buffers: shared hit=191
                      ->  Nested Loop  (cost=0.86..12.90 rows=1 width=32) (actual time=0.003..0.004 rows=2 loops=10)
                            Output: cl2.id, cl2.liked_by
                            Buffers: shared hit=111
                            ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl2  (cost=0.43..8.45 rows=1 width=48) (actual time=0.002..0.002 rows=2 loops=10)
                                  Output: cl2.id, cl2.comment_id, cl2.liked_by
                                  Index Cond: (cl2.comment_id = c.id)
                                  Buffers: shared hit=50
                            ->  Index Only Scan using pk_comments on public.comments c2  (cost=0.43..4.45 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                                  Output: c2.id
                                  Index Cond: (c2.id = c.id)
                                  Heap Fetches: 0
                                  Buffers: shared hit=61
                      ->  Index Scan using pk_users on public.users liked_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=20)
                            Output: liked_user.id, liked_user.email, liked_user.nickname, liked_user.password, liked_user.created_at, liked_user.updated_at, liked_user.deleted_at
                            Index Cond: (liked_user.id = cl2.liked_by)
                            Filter: (liked_user.deleted_at IS NULL)
                            Buffers: shared hit=80
Planning:
  Buffers: shared hit=426
Planning Time: 1.407 ms
Execution Time: 9.646 ms
```

### exclusion after

```text
Limit  (cost=1.71..195.67 rows=10 width=168) (actual time=1.180..1.225 rows=10 loops=1)
  Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=426
  ->  Nested Loop  (cost=1.71..350874.75 rows=18090 width=168) (actual time=1.179..1.223 rows=10 loops=1)
        Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
        Inner Unique: true
        Buffers: shared hit=426
        ->  Nested Loop  (cost=1.28..188658.22 rows=18090 width=133) (actual time=1.157..1.177 rows=10 loops=1)
              Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
              Inner Unique: true
              Buffers: shared hit=336
              ->  Nested Loop  (cost=0.86..180684.65 rows=18090 width=112) (actual time=1.148..1.159 rows=10 loops=1)
                    Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.user_id, c.article_id
                    Inner Unique: true
                    Buffers: shared hit=296
                    ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..62837.37 rows=18090 width=40) (actual time=1.135..1.136 rows=10 loops=1)
                          Output: cl.id, cl.created_at, cl.comment_id
                          Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                          Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                          Rows Removed by Filter: 10000
                          Buffers: shared hit=256
                    ->  Index Scan using pk_comments on public.comments c  (cost=0.43..6.51 rows=1 width=88) (actual time=0.002..0.002 rows=1 loops=10)
                          Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                          Index Cond: (c.id = cl.comment_id)
                          Buffers: shared hit=40
              ->  Index Scan using pk_users on public.users u  (cost=0.42..0.44 rows=1 width=37) (actual time=0.002..0.002 rows=1 loops=10)
                    Output: u.id, u.email, u.nickname, u.password, u.created_at, u.updated_at, u.deleted_at
                    Index Cond: (u.id = c.user_id)
                    Buffers: shared hit=40
        ->  Index Scan using pk_articles on public.articles a  (cost=0.43..0.50 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
              Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
              Index Cond: (a.id = c.article_id)
              Buffers: shared hit=40
        SubPlan 1
          ->  Aggregate  (cost=8.45..8.46 rows=1 width=8) (actual time=0.002..0.002 rows=1 loops=10)
                Output: count(cl2.id)
                Buffers: shared hit=50
                ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl2  (cost=0.43..8.45 rows=1 width=16) (actual time=0.001..0.002 rows=2 loops=10)
                      Output: cl2.id
                      Index Cond: (cl2.comment_id = c.id)
                      Filter: ((cl2.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=50
Planning:
  Buffers: shared hit=408
Planning Time: 1.098 ms
Execution Time: 1.276 ms
```

## 기술 근거와 개선 방향

### 측정에서 확인한 내용

- 삭제 좋아요가 많은 조건에서 변경 전 SQL은 최근순 좋아요 10,010건마다 댓글 정보를 기본 키(PK)로 확인했다. 이 중 10,000건은 최종 결과에서 제외됐다.
- 변경 후에는 `comment_likes.visibility_status`로 먼저 제외한다. 댓글 정보는 최종 결과 10건에 대해서만 읽는다.
- 연결 데이터가 많은 조건의 `rows=7948`은 DB가 실행 전에 예상한 값이다. 실제 인덱스가 반환한 값은 `actual rows=10`이다.
- 최신 10건이 모두 `ACTIVE`라서 이 조건에서는 활성 데이터 전용 인덱스를 사용해도 읽는 행 수가 크게 줄지 않는다.
- 변경 후 좋아요 수 계산은 댓글마다 평균 2건을 10회 확인한다. 메모리 블록도 50개만 읽어 현재의 큰 병목은 아니다.
- 변경 전 좋아요 수 계산에 있던 `comments c2` 조인은 테이블 본문을 읽지 않고 기본 키 인덱스만 확인한다. 실행계획에는 `Index Only Scan`으로 표시된다.

### 후속 개선 후보

- 삭제 좋아요가 많은 조건에서는 `idx_comment_likes_liked_by_created_id`가 10,010건을 읽고 10,000건을 제외한다.
- 같은 키에 `INCLUDE(comment_id) WHERE visibility_status = 'ACTIVE'`를 적용한 부분 인덱스를 추가한다. 비활성 좋아요를 처음부터 읽지 않고 결과에 필요한 `comment_id`도 인덱스에서 가져올 수 있다.
- `uk_comment_likes_comment_liked_by`는 한 사용자가 같은 댓글에 좋아요를 중복 등록하지 못하게 한다. 이 UNIQUE 인덱스는 변경하거나 제거하지 않는다.
- `(comment_id) WHERE visibility_status = 'ACTIVE'` 부분 인덱스는 댓글당 좋아요가 훨씬 많은 조회와 함께 검증할 낮은 우선순위 후보다.
- `COUNT(*)`를 사용하면 `id`를 인덱스에 추가하지 않아도 된다. 현재의 `COUNT(CL2)`를 유지한다면 인덱스에 `id`를 함께 저장하는 `INCLUDE(id)`가 필요하다.
