# 최근 작성 댓글 실행계획

이 조회는 사용자가 최근에 작성한 활성 댓글 10건과 각 댓글의 활성 좋아요 수를 가져온다. 변경 전에는 좋아요를 누른 사용자가 삭제되었는지 한 명씩 확인했다. 변경 후에는 좋아요 데이터의 `visibility_status`만 확인한다.

## 한눈에 보기

- 연결된 좋아요가 많은 조건에서는 실행 시간이 `10.236 ms`에서 `3.111 ms`로 69.6% 줄었다.
- 삭제 댓글이 많이 쌓인 조건에서는 `2.941 ms`에서 `3.136 ms`로 6.6% 늘어 개선되지 않았다.
- 다음 개선의 우선순위는 비활성 댓글 10,000건을 읽지 않도록 활성 댓글만 담는 인덱스를 추가하는 것이다.
- 좋아요 수 계산용 인덱스는 테이블 본문을 다시 읽는 비용은 줄일 수 있지만, 계산해야 하는 좋아요 수 자체는 줄이지 못한다.

## 실행 SQL

- before: [before/recent-comments.sql](../../scripts/performance/activity-history/mid4-227/before/recent-comments.sql)
- after: [after/recent-comments.sql](../../scripts/performance/activity-history/mid4-227/after/recent-comments.sql)

before (`44dcf82`):

```sql
SELECT c.id, a.id AS article_id, a.title AS article_title,
       u.id AS user_id, u.nickname AS user_nickname, c.content,
       (SELECT count(cl.id)
          FROM comment_likes cl
          JOIN users liked_user ON liked_user.id = cl.liked_by
         WHERE cl.comment_id = c.id
           AND liked_user.deleted_at IS NULL) AS like_count,
       c.created_at
  FROM comments c
  JOIN users u ON u.id = c.user_id
  JOIN articles a ON a.id = c.article_id
 WHERE u.id = '00000001-0000-4000-8000-000000000001'
   AND u.deleted_at IS NULL
   AND c.deleted_at IS NULL
   AND a.deleted_at IS NULL
 ORDER BY c.created_at DESC, c.id DESC
 FETCH FIRST 10 ROWS ONLY;
```

after (`9c195bd`):

```sql
SELECT c.id, a.id AS article_id, a.title AS article_title,
       u.id AS user_id, u.nickname AS user_nickname, c.content,
       (SELECT count(cl.id)
          FROM comment_likes cl
         WHERE cl.comment_id = c.id
           AND cl.visibility_status = 'ACTIVE') AS like_count,
       c.created_at
  FROM comments c
  JOIN users u ON u.id = c.user_id
  JOIN articles a ON a.id = c.article_id
 WHERE u.id = '00000001-0000-4000-8000-000000000001'
   AND c.visibility_status = 'ACTIVE'
 ORDER BY c.created_at DESC, c.id DESC
 FETCH FIRST 10 ROWS ONLY;
```

## 반복 측정

같은 데이터와 DB 설정에서 변경 전후 SQL을 비교했다. 각 SQL을 세 번 미리 실행한 다음, 실제 속도를 확인하는 일반 SELECT와 처리 과정을 분석하는 EXPLAIN을 각각 다섯 번 측정했다.

표의 EXPLAIN 값과 아래 실행계획 원문은 같은 조건에서 별도로 측정했다. 실행할 때마다 작은 차이가 생길 수 있으므로 원문의 `Execution Time`은 표의 중앙값과 다를 수 있다. 단위는 ms다.

| 데이터 조건 | 구분 | 일반 SELECT 5회 | 중앙값 | EXPLAIN 5회 | 중앙값 |
| --- | --- | --- | ---: | --- | ---: |
| fanout | before | 9.746, 10.769, 9.370, 10.236, 16.834 | 10.236 | 9.155, 8.873, 8.946, 8.938, 9.173 | 8.946 |
| fanout | after | 2.990, 3.137, 3.031, 3.111, 3.185 | 3.111 | 1.700, 1.733, 1.734, 1.786, 1.769 | 1.734 |
| exclusion | before | 2.941, 2.832, 2.891, 2.966, 3.106 | 2.941 | 1.297, 1.328, 1.406, 1.299, 1.363 | 1.328 |
| exclusion | after | 3.072, 3.188, 3.136, 3.035, 3.151 | 3.136 | 1.427, 1.455, 1.479, 1.408, 1.713 | 1.455 |

연결된 좋아요가 많은 조건의 일반 SELECT 중앙값은 69.6% 줄었다. 반면 삭제 댓글이 많은 조건은 6.6% 늘었다. 삭제 댓글 후보 10,000건을 인덱스에서 읽은 뒤 제외하는 과정이 변경 전후에 모두 남아 있기 때문이다.

## 동일 조건 실행계획 원문

아래 코드 블록은 PostgreSQL이 출력한 내용을 그대로 기록한 것이다. 처음 읽을 때는 다음 항목을 확인하면 된다.

- `actual time`: 실제 처리 시간
- `rows`: 한 번에 처리한 행 수
- `loops`: 같은 단계가 반복된 횟수
- `Rows Removed by Filter`: 데이터를 읽은 뒤 조건에 맞지 않아 제외한 행 수
- `Buffers`: 읽은 데이터 블록 수. `shared hit`는 메모리, `read`는 디스크에서 읽었다는 뜻이다.

### fanout before

```text
Limit  (cost=1.27..276.94 rows=10 width=138) (actual time=0.936..8.393 rows=10 loops=1)
  Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=40353
  ->  Nested Loop  (cost=1.27..278253.62 rows=10094 width=138) (actual time=0.935..8.391 rows=10 loops=1)
        Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
        Buffers: shared hit=40353
        ->  Nested Loop  (cost=0.86..107579.25 rows=10094 width=115) (actual time=0.017..0.033 rows=10 loops=1)
              Output: c.id, c.content, c.created_at, c.user_id, a.id, a.title
              Inner Unique: true
              Buffers: shared hit=44
              ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..40820.19 rows=10299 width=88) (actual time=0.011..0.015 rows=10 loops=1)
                    Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                    Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (c.deleted_at IS NULL)
                    Buffers: shared hit=4
              ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.48 rows=1 width=43) (actual time=0.001..0.001 rows=1 loops=10)
                    Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
                    Index Cond: (a.id = c.article_id)
                    Filter: (a.deleted_at IS NULL)
                    Buffers: shared hit=40
        ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.001..0.001 rows=1 loops=10)
              Output: u.id, u.nickname
              Buffers: shared hit=4
              ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=31) (actual time=0.004..0.005 rows=1 loops=1)
                    Output: u.id, u.nickname
                    Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (u.deleted_at IS NULL)
                    Buffers: shared hit=4
        SubPlan 1
          ->  Aggregate  (cost=16.89..16.90 rows=1 width=8) (actual time=0.833..0.833 rows=1 loops=10)
                Output: count(cl.id)
                Buffers: shared hit=40305
                ->  Nested Loop  (cost=0.85..16.88 rows=1 width=16) (actual time=0.005..0.799 rows=1001 loops=10)
                      Output: cl.id
                      Inner Unique: true
                      Buffers: shared hit=40305
                      ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl  (cost=0.43..8.45 rows=1 width=32) (actual time=0.004..0.103 rows=1001 loops=10)
                            Output: cl.id, cl.liked_by
                            Index Cond: (cl.comment_id = c.id)
                            Buffers: shared hit=265
                      ->  Index Scan using pk_users on public.users liked_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=10010)
                            Output: liked_user.id, liked_user.email, liked_user.nickname, liked_user.password, liked_user.created_at, liked_user.updated_at, liked_user.deleted_at
                            Index Cond: (liked_user.id = cl.liked_by)
                            Filter: (liked_user.deleted_at IS NULL)
                            Buffers: shared hit=40040
Planning:
  Buffers: shared hit=399
Planning Time: 0.897 ms
Execution Time: 8.454 ms
```

### fanout after

```text
Limit  (cost=1.27..191.60 rows=10 width=138) (actual time=0.203..1.648 rows=10 loops=1)
  Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=313
  ->  Nested Loop  (cost=1.27..192034.50 rows=10090 width=138) (actual time=0.202..1.646 rows=10 loops=1)
        Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
        Inner Unique: true
        Buffers: shared hit=313
        ->  Nested Loop  (cost=0.85..40981.83 rows=10090 width=103) (actual time=0.028..0.037 rows=10 loops=1)
              Output: c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
              Buffers: shared hit=8
              ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..40847.26 rows=10090 width=88) (actual time=0.012..0.016 rows=10 loops=1)
                    Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                    Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                    Buffers: shared hit=4
              ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.001..0.002 rows=1 loops=10)
                    Output: u.id, u.nickname
                    Buffers: shared hit=4
                    ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=31) (actual time=0.011..0.011 rows=1 loops=1)
                          Output: u.id, u.nickname
                          Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Buffers: shared hit=4
        ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.51 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
              Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
              Index Cond: (a.id = c.article_id)
              Buffers: shared hit=40
        SubPlan 1
          ->  Aggregate  (cost=8.45..8.46 rows=1 width=8) (actual time=0.158..0.158 rows=1 loops=10)
                Output: count(cl.id)
                Buffers: shared hit=265
                ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl  (cost=0.43..8.45 rows=1 width=16) (actual time=0.004..0.126 rows=1001 loops=10)
                      Output: cl.id
                      Index Cond: (cl.comment_id = c.id)
                      Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=265
Planning:
  Buffers: shared hit=374
Planning Time: 0.665 ms
Execution Time: 1.693 ms
```

### exclusion before

```text
Limit  (cost=1.28..263.61 rows=10 width=144) (actual time=1.676..1.714 rows=10 loops=1)
  Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=448
  ->  Nested Loop  (cost=1.28..511611.59 rows=19502 width=144) (actual time=1.675..1.711 rows=10 loops=1)
        Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
        Buffers: shared hit=448
        ->  Nested Loop  (cost=0.86..181821.16 rows=19502 width=115) (actual time=1.629..1.641 rows=10 loops=1)
              Output: c.id, c.content, c.created_at, c.user_id, a.id, a.title
              Inner Unique: true
              Buffers: shared hit=364
              ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..74786.94 rows=20005 width=88) (actual time=1.617..1.620 rows=10 loops=1)
                    Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                    Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (c.deleted_at IS NULL)
                    Rows Removed by Filter: 10000
                    Buffers: shared hit=324
              ->  Index Scan using pk_articles on public.articles a  (cost=0.43..5.35 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
                    Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
                    Index Cond: (a.id = c.article_id)
                    Filter: (a.deleted_at IS NULL)
                    Buffers: shared hit=40
        ->  Materialize  (cost=0.42..8.44 rows=1 width=37) (actual time=0.001..0.001 rows=1 loops=10)
              Output: u.id, u.nickname
              Buffers: shared hit=4
              ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=37) (actual time=0.008..0.008 rows=1 loops=1)
                    Output: u.id, u.nickname
                    Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: (u.deleted_at IS NULL)
                    Buffers: shared hit=4
        SubPlan 1
          ->  Aggregate  (cost=16.89..16.90 rows=1 width=8) (actual time=0.005..0.005 rows=1 loops=10)
                Output: count(cl.id)
                Buffers: shared hit=80
                ->  Nested Loop  (cost=0.85..16.89 rows=1 width=16) (actual time=0.004..0.004 rows=1 loops=10)
                      Output: cl.id
                      Inner Unique: true
                      Buffers: shared hit=80
                      ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl  (cost=0.43..8.45 rows=1 width=32) (actual time=0.002..0.002 rows=1 loops=10)
                            Output: cl.id, cl.liked_by
                            Index Cond: (cl.comment_id = c.id)
                            Buffers: shared hit=40
                      ->  Index Scan using pk_users on public.users liked_user  (cost=0.42..8.44 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=10)
                            Output: liked_user.id, liked_user.email, liked_user.nickname, liked_user.password, liked_user.created_at, liked_user.updated_at, liked_user.deleted_at
                            Index Cond: (liked_user.id = cl.liked_by)
                            Filter: (liked_user.deleted_at IS NULL)
                            Buffers: shared hit=40
Planning:
  Buffers: shared hit=395
Planning Time: 1.321 ms
Execution Time: 1.781 ms
```

### exclusion after

```text
Limit  (cost=1.28..178.10 rows=10 width=144) (actual time=1.847..1.875 rows=10 loops=1)
  Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
  Buffers: shared hit=408
  ->  Nested Loop  (cost=1.28..346855.03 rows=19616 width=144) (actual time=1.846..1.873 rows=10 loops=1)
        Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
        Inner Unique: true
        Buffers: shared hit=408
        ->  Nested Loop  (cost=0.85..75093.51 rows=19616 width=109) (actual time=1.815..1.820 rows=10 loops=1)
              Output: c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
              Buffers: shared hit=328
              ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..74839.87 rows=19616 width=88) (actual time=1.789..1.791 rows=10 loops=1)
                    Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                    Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                    Rows Removed by Filter: 10000
                    Buffers: shared hit=324
              ->  Materialize  (cost=0.42..8.44 rows=1 width=37) (actual time=0.002..0.003 rows=1 loops=10)
                    Output: u.id, u.nickname
                    Buffers: shared hit=4
                    ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=37) (actual time=0.020..0.020 rows=1 loops=1)
                          Output: u.id, u.nickname
                          Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Buffers: shared hit=4
        ->  Index Scan using pk_articles on public.articles a  (cost=0.43..5.39 rows=1 width=43) (actual time=0.001..0.001 rows=1 loops=10)
              Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
              Index Cond: (a.id = c.article_id)
              Buffers: shared hit=40
        SubPlan 1
          ->  Aggregate  (cost=8.45..8.46 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                Output: count(cl.id)
                Buffers: shared hit=40
                ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl  (cost=0.43..8.45 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=10)
                      Output: cl.id
                      Index Cond: (cl.comment_id = c.id)
                      Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                      Buffers: shared hit=40
Planning:
  Buffers: shared hit=374
Planning Time: 0.981 ms
Execution Time: 1.929 ms
```

## 기술 근거와 개선 방향

### 측정에서 확인한 내용

- 변경 전에는 좋아요를 누른 사용자 정보를 기본 키(PK)로 10,010회 확인했다. 변경 후에는 이 반복 조회가 사라졌다.
- 그 결과 메모리에서 읽은 블록 수(`shared hit`)가 40,353에서 313으로 줄었다.
- 변경 후에도 댓글 10건의 좋아요는 각각 계산한다. 댓글마다 1,001건을 읽으므로 총 10,010건을 처리한다.
- 조회된 좋아요가 모두 `ACTIVE`라서 제외된 행은 없었다. 활성 데이터만 담는 인덱스를 추가해도 계산할 행 수는 그대로다.
- 삭제 댓글이 많은 조건에서는 비활성 댓글 10,000건을 읽은 뒤 제외하는 구간에 약 1.2~1.3 ms가 걸렸다. 이때 사용한 인덱스는 `idx_comments_user_created_id`다.

### 후속 개선 후보

- `idx_comments_user_created_id`와 같은 키에 `WHERE visibility_status = 'ACTIVE'`를 적용한 부분 인덱스를 추가한다.
- 이 인덱스는 활성 댓글만 최신순으로 읽는다. 필요한 10건을 찾으면 바로 조회를 끝낼 수 있어 가장 직접적인 개선 후보다.
- 좋아요 수 계산용 부분 인덱스와 `COUNT(*)`는 계산할 행 수를 줄이지는 못한다. 대신 테이블 본문(heap)을 다시 읽는 비용을 줄이고 인덱스만 읽는 방식(`Index Only Scan`)을 사용할 가능성을 높인다.
- DB의 데이터 변경 상태에 따라 인덱스만으로 확인할 수 없는 행은 테이블을 다시 읽을 수 있다. 이 횟수는 `Heap Fetches`에 표시된다.
- 이미 배포된 `V202608251910__add_activity_history_indexes.sql`은 수정하지 않는다. 파일을 바꾸면 Flyway가 기록한 확인값(checksum)과 달라지기 때문이다.
- 새로운 DB 변경 이력 파일(migration)에 새 이름의 인덱스를 추가한다. 효과를 측정한 뒤 기존 인덱스 제거 여부를 결정한다.
- 좋아요 수 계산용 인덱스와 `COUNT(*)` 변경은 MID4-227에 포함하지 않고 별도 작업에서 검증한다.
