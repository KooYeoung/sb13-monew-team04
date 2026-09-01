# 구독 관심사 실행계획

> [MID4-227 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 조회는 사용자가 구독한 활성 관심사와 관심사별 활성 구독자 수를 가져온다. 변경 전에는 구독자가 삭제되었는지 한 명씩 확인했다. 변경 후에는 구독 데이터의 `visibility_status`를 바로 확인한다.

## 한눈에 보기

- 연결된 구독이 많은 조건에서는 실행 시간이 `41.923 ms`에서 `7.258 ms`로 82.7% 줄었다.
- 비활성 구독이 많이 쌓인 조건에서는 `62.931 ms`에서 `19.494 ms`로 69.0% 줄었다.
- 변경 후에도 관심사 50개의 구독자 수를 각각 계산한다. 이 과정이 현재 가장 큰 병목이다.
- 활성 구독만 담는 인덱스는 테이블 본문을 다시 읽는 비용을 줄일 수 있지만, 50번 반복되는 계산은 줄이지 못한다.

## 실행 SQL

- before: [before/subscribed-interests.sql](../../scripts/performance/activity-history/mid4-227/before/subscribed-interests.sql)
- after: [after/subscribed-interests.sql](../../scripts/performance/activity-history/mid4-227/after/subscribed-interests.sql)

before (`44dcf82`):

```sql
SELECT s.id, s.created_at, i.id AS interest_id,
       i.name, i.created_at AS interest_created_at,
       i.updated_at AS interest_updated_at,
       (SELECT count(s2.id)
          FROM subscriptions s2
          JOIN users subscriber ON subscriber.id = s2.user_id
          JOIN interests i2 ON i2.id = s2.interest_id
         WHERE i2.id = i.id
           AND subscriber.deleted_at IS NULL) AS interest_subscriber_count
  FROM subscriptions s
  JOIN interests i ON i.id = s.interest_id
  JOIN users u ON u.id = s.user_id
 WHERE s.user_id = '00000001-0000-4000-8000-000000000001'
   AND u.deleted_at IS NULL
 ORDER BY s.created_at DESC, s.id DESC;
```

after (`9c195bd`):

```sql
SELECT s.id, s.created_at, i.id AS interest_id,
       i.created_at AS interest_created_at, i.name,
       i.updated_at AS interest_updated_at,
       (SELECT count(s2.id)
          FROM subscriptions s2
         WHERE s2.interest_id = i.id
           AND s2.visibility_status = 'ACTIVE') AS subscriber_count
  FROM subscriptions s
  JOIN interests i ON i.id = s.interest_id
 WHERE s.user_id = '00000001-0000-4000-8000-000000000001'
   AND s.visibility_status = 'ACTIVE'
 ORDER BY s.created_at DESC, s.id DESC;
```

## 반복 측정

같은 데이터와 DB 설정에서 변경 전후 SQL을 비교했다. 각 SQL을 세 번 미리 실행한 다음, 실제 속도를 확인하는 일반 SELECT와 처리 과정을 분석하는 EXPLAIN을 각각 다섯 번 측정했다.

표의 EXPLAIN 값과 아래 실행계획 원문은 같은 조건에서 별도로 측정했다. 실행할 때마다 작은 차이가 생길 수 있으므로 원문의 `Execution Time`은 표의 중앙값과 다를 수 있다. 단위는 ms다.

| 데이터 조건 | 구분 | 일반 SELECT 5회 | 중앙값 | EXPLAIN 5회 | 중앙값 |
| --- | --- | --- | ---: | --- | ---: |
| fanout | before | 41.923, 41.722, 42.048, 41.606, 42.532 | 41.923 | 48.846, 49.144, 49.446, 48.567, 48.871 | 48.871 |
| fanout | after | 7.301, 7.251, 7.258, 7.317, 7.256 | 7.258 | 7.994, 7.750, 7.633, 7.970, 7.689 | 7.750 |
| exclusion | before | 63.816, 62.931, 63.043, 62.467, 62.514 | 62.931 | 65.933, 65.206, 66.019, 63.024, 64.990 | 65.206 |
| exclusion | after | 20.003, 22.307, 19.380, 19.494, 19.301 | 19.494 | 17.925, 17.365, 17.662, 18.782, 17.434 | 17.662 |

일반 SELECT 중앙값은 연결된 데이터가 많은 조건에서 82.7%, 비활성 구독이 많은 조건에서 69.0% 줄었다. 변경 후 연결 데이터 조건의 다섯 번 측정값은 7.251~7.317 ms로 차이가 작았다. 대표 실행계획의 측정값은 7.750 ms다.

## 동일 조건 실행계획 원문

아래 코드 블록은 PostgreSQL이 출력한 내용을 그대로 기록한 것이다. 처음 읽을 때는 다음 항목을 확인하면 된다.

- `actual time`: 실제 처리 시간
- `rows`: 한 번에 처리한 행 수
- `loops`: 같은 단계가 반복된 횟수
- `Rows Removed by Filter`: 데이터를 읽은 뒤 조건에 맞지 않아 제외한 행 수
- `Buffers`: 읽은 데이터 블록 수. `shared hit`는 메모리, `read`는 디스크에서 읽었다는 뜻이다.

### fanout before

```text
Nested Loop  (cost=1.13..1230.07 rows=7 width=78) (actual time=1.056..46.233 rows=50 loops=1)
  Output: s.id, s.created_at, i.id, i.name, i.created_at, i.updated_at, (SubPlan 1)
  Buffers: shared hit=204353
  ->  Nested Loop  (cost=0.71..90.57 rows=7 width=86) (actual time=0.013..0.081 rows=50 loops=1)
        Output: s.id, s.created_at, s.user_id, i.id, i.name, i.created_at, i.updated_at
        Inner Unique: true
        Buffers: shared hit=154
        ->  Index Scan using idx_subscriptions_user_created_id on public.subscriptions s  (cost=0.42..32.41 rows=7 width=56) (actual time=0.007..0.021 rows=50 loops=1)
              Output: s.id, s.created_at, s.interest_id, s.user_id
              Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
              Buffers: shared hit=4
        ->  Index Scan using pk_interests on public.interests i  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
              Output: i.id, i.name, i.created_at, i.updated_at
              Index Cond: (i.id = s.interest_id)
              Buffers: shared hit=150
  ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.000..0.000 rows=1 loops=50)
        Output: u.id
        Buffers: shared hit=4
        ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=16) (actual time=0.004..0.004 rows=1 loops=1)
              Output: u.id
              Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (u.deleted_at IS NULL)
              Buffers: shared hit=4
  SubPlan 1
    ->  Aggregate  (cost=161.56..161.57 rows=1 width=8) (actual time=0.922..0.922 rows=1 loops=50)
          Output: count(s2.id)
          Buffers: shared hit=204195
          ->  Nested Loop  (cost=1.13..161.53 rows=12 width=16) (actual time=0.005..0.888 rows=1011 loops=50)
                Output: s2.id
                Buffers: shared hit=204195
                ->  Index Only Scan using pk_interests on public.interests i2  (cost=0.29..8.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Output: i2.id
                      Index Cond: (i2.id = i.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Nested Loop  (cost=0.84..153.10 rows=12 width=32) (actual time=0.004..0.832 rows=1011 loops=50)
                      Output: s2.id, s2.interest_id
                      Inner Unique: true
                      Buffers: shared hit=204045
                      ->  Index Scan using uk_subscriptions_interest_user on public.subscriptions s2  (cost=0.42..51.88 rows=12 width=48) (actual time=0.003..0.113 rows=1011 loops=50)
                            Output: s2.id, s2.user_id, s2.interest_id
                            Index Cond: (s2.interest_id = i.id)
                            Buffers: shared hit=1865
                      ->  Index Scan using pk_users on public.users subscriber  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50545)
                            Output: subscriber.id, subscriber.email, subscriber.nickname, subscriber.password, subscriber.created_at, subscriber.updated_at, subscriber.deleted_at
                            Index Cond: (subscriber.id = s2.user_id)
                            Filter: (subscriber.deleted_at IS NULL)
                            Rows Removed by Filter: 0
                            Buffers: shared hit=202180
Planning:
  Buffers: shared hit=295
Planning Time: 0.643 ms
Execution Time: 46.305 ms
```

### fanout after

```text
Nested Loop  (cost=0.71..448.76 rows=7 width=78) (actual time=0.193..7.253 rows=50 loops=1)
  Output: s.id, s.created_at, i.id, i.created_at, i.name, i.updated_at, (SubPlan 1)
  Inner Unique: true
  Buffers: shared hit=1944
  ->  Index Scan using idx_subscriptions_user_created_id on public.subscriptions s  (cost=0.42..32.43 rows=7 width=40) (actual time=0.013..0.026 rows=50 loops=1)
        Output: s.id, s.created_at, s.interest_id
        Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
        Filter: ((s.visibility_status)::text = 'ACTIVE'::text)
        Buffers: shared hit=4
  ->  Index Scan using pk_interests on public.interests i  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
        Output: i.id, i.name, i.created_at, i.updated_at
        Index Cond: (i.id = s.interest_id)
        Buffers: shared hit=150
  SubPlan 1
    ->  Aggregate  (cost=51.16..51.17 rows=1 width=8) (actual time=0.143..0.143 rows=1 loops=50)
          Output: count(s2.id)
          Buffers: shared hit=1790
          ->  Bitmap Heap Scan on public.subscriptions s2  (cost=4.52..51.13 rows=12 width=16) (actual time=0.031..0.110 rows=1011 loops=50)
                Output: s2.id
                Recheck Cond: (s2.interest_id = i.id)
                Filter: ((s2.visibility_status)::text = 'ACTIVE'::text)
                Rows Removed by Filter: 0
                Heap Blocks: exact=1125
                Buffers: shared hit=1790
                ->  Bitmap Index Scan on uk_subscriptions_interest_user  (cost=0.00..4.51 rows=12 width=0) (actual time=0.030..0.030 rows=1011 loops=50)
                      Index Cond: (s2.interest_id = i.id)
                      Buffers: shared hit=665
Planning:
  Buffers: shared hit=244
Planning Time: 0.508 ms
Execution Time: 7.302 ms
```

### exclusion before

```text
Nested Loop  (cost=1.14..861.19 rows=5 width=78) (actual time=2.524..62.871 rows=50 loops=1)
  Output: s.id, s.created_at, i.id, i.name, i.created_at, i.updated_at, (SubPlan 1)
  Buffers: shared hit=234494
  ->  Nested Loop  (cost=0.71..64.63 rows=5 width=86) (actual time=0.013..0.105 rows=50 loops=1)
        Output: s.id, s.created_at, s.user_id, i.id, i.name, i.created_at, i.updated_at
        Inner Unique: true
        Buffers: shared hit=154
        ->  Index Scan using idx_subscriptions_user_created_id on public.subscriptions s  (cost=0.42..23.10 rows=5 width=56) (actual time=0.007..0.023 rows=50 loops=1)
              Output: s.id, s.created_at, s.interest_id, s.user_id
              Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
              Buffers: shared hit=4
        ->  Index Scan using pk_interests on public.interests i  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
              Output: i.id, i.name, i.created_at, i.updated_at
              Index Cond: (i.id = s.interest_id)
              Buffers: shared hit=150
  ->  Materialize  (cost=0.42..8.44 rows=1 width=16) (actual time=0.000..0.001 rows=1 loops=50)
        Output: u.id
        Buffers: shared hit=4
        ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=16) (actual time=0.010..0.010 rows=1 loops=1)
              Output: u.id
              Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (u.deleted_at IS NULL)
              Buffers: shared hit=4
  SubPlan 1
    ->  Aggregate  (cost=157.60..157.61 rows=1 width=8) (actual time=1.253..1.253 rows=1 loops=50)
          Output: count(s2.id)
          Buffers: shared hit=234336
          ->  Nested Loop  (cost=1.14..157.58 rows=8 width=16) (actual time=0.006..1.251 rows=11 loops=50)
                Output: s2.id
                Buffers: shared hit=234336
                ->  Index Only Scan using pk_interests on public.interests i2  (cost=0.29..4.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Output: i2.id
                      Index Cond: (i2.id = i.id)
                      Heap Fetches: 0
                      Buffers: shared hit=101
                ->  Nested Loop  (cost=0.84..153.19 rows=8 width=32) (actual time=0.005..1.249 rows=11 loops=50)
                      Output: s2.id, s2.interest_id
                      Inner Unique: true
                      Buffers: shared hit=234235
                      ->  Index Scan using uk_subscriptions_interest_user on public.subscriptions s2  (cost=0.42..51.94 rows=12 width=48) (actual time=0.003..0.310 rows=1011 loops=50)
                            Output: s2.id, s2.user_id, s2.interest_id
                            Index Cond: (s2.interest_id = i.id)
                            Buffers: shared hit=32055
                      ->  Index Scan using pk_users on public.users subscriber  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=0 loops=50545)
                            Output: subscriber.id, subscriber.email, subscriber.nickname, subscriber.password, subscriber.created_at, subscriber.updated_at, subscriber.deleted_at
                            Index Cond: (subscriber.id = s2.user_id)
                            Filter: (subscriber.deleted_at IS NULL)
                            Rows Removed by Filter: 1
                            Buffers: shared hit=202180
Planning:
  Buffers: shared hit=295
Planning Time: 0.684 ms
Execution Time: 62.950 ms
```

### exclusion after

```text
Nested Loop  (cost=0.71..261.03 rows=4 width=78) (actual time=1.237..16.848 rows=50 loops=1)
  Output: s.id, s.created_at, i.id, i.created_at, i.name, i.updated_at, (SubPlan 1)
  Inner Unique: true
  Buffers: shared hit=32172
  ->  Index Scan using idx_subscriptions_user_created_id on public.subscriptions s  (cost=0.42..23.11 rows=4 width=40) (actual time=0.012..0.034 rows=50 loops=1)
        Output: s.id, s.created_at, s.interest_id
        Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
        Filter: ((s.visibility_status)::text = 'ACTIVE'::text)
        Buffers: shared hit=4
  ->  Index Scan using pk_interests on public.interests i  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
        Output: i.id, i.name, i.created_at, i.updated_at
        Index Cond: (i.id = s.interest_id)
        Buffers: shared hit=150
  SubPlan 1
    ->  Aggregate  (cost=51.16..51.17 rows=1 width=8) (actual time=0.334..0.334 rows=1 loops=50)
          Output: count(s2.id)
          Buffers: shared hit=32018
          ->  Bitmap Heap Scan on public.subscriptions s2  (cost=4.52..51.13 rows=11 width=16) (actual time=0.072..0.332 rows=11 loops=50)
                Output: s2.id
                Recheck Cond: (s2.interest_id = i.id)
                Filter: ((s2.visibility_status)::text = 'ACTIVE'::text)
                Rows Removed by Filter: 1000
                Heap Blocks: exact=31383
                Buffers: shared hit=32018
                ->  Bitmap Index Scan on uk_subscriptions_interest_user  (cost=0.00..4.51 rows=12 width=0) (actual time=0.046..0.046 rows=1011 loops=50)
                      Index Cond: (s2.interest_id = i.id)
                      Buffers: shared hit=635
Planning:
  Buffers: shared hit=244
Planning Time: 0.552 ms
Execution Time: 16.903 ms
```

## 기술 근거와 개선 방향

### 측정에서 확인한 내용

- 변경 전에는 관심사 50개의 구독자 50,545명이 삭제되었는지 기본 키(PK)로 확인했다.
- 변경 후에는 구독 데이터의 상태를 직접 확인한다. 메모리에서 읽은 블록 수(`shared hit`)는 204,353에서 1,944로 줄었다.
- 기존 `uk_subscriptions_interest_user` 인덱스는 관심사별 구독을 찾을 수 있지만 활성 상태는 알 수 없다. 따라서 후보를 찾은 뒤 테이블 본문에서 상태를 다시 확인한다.
- 실행계획에서 후보 위치를 모으는 단계는 `Bitmap Index Scan`, 테이블에서 상태를 확인하는 단계는 `Bitmap Heap Scan`과 `Filter`로 표시된다.
- 연결된 구독이 많은 조건에서는 관심사마다 1,011건을 읽고(`rows=1011`) 이 작업을 50번 반복한다(`loops=50`). 총 약 50,550건이 모두 `ACTIVE`다.
- 비활성 구독이 많은 조건에서는 관심사마다 1,011건을 읽은 뒤 1,000건을 제외한다. 테이블 블록도 31,383개 읽었다(`Heap Blocks: exact=31383`).

### 후속 개선 후보

- `(interest_id) WHERE visibility_status = 'ACTIVE'` 부분 인덱스를 추가한다. 비활성 구독이 많은 조건에서는 관심사마다 필요한 11건만 읽을 수 있다.
- 집계를 `COUNT(*)`로 바꾸면 테이블 본문(heap)을 다시 읽는 비용을 줄이고 인덱스만 읽는 방식(`Index Only Scan`)을 기대할 수 있다.
- 현재의 `COUNT(s2.id)`를 유지한다면 인덱스에 `id`를 함께 저장하는 `INCLUDE(id)`가 필요하다.
- 기존 UNIQUE 인덱스는 같은 사용자가 같은 관심사를 중복 구독하지 못하게 하므로 변경하거나 제거하지 않는다.
- 사용자의 구독 목록에 쓰는 `idx_subscriptions_user_created_id`도 활성 구독 전용 부분 인덱스 후보가 될 수 있다. 다만 현재는 50건을 약 0.03 ms에 읽어 우선순위가 낮다.
- 부분 인덱스는 테이블을 다시 읽는 비용은 줄이지만 50번 반복되는 계산은 없애지 못한다.
- 연결 데이터가 더 늘어나면 모든 관심사의 구독자 수를 한 번에 계산하는 `GROUP BY interest_id` SQL을 검토한다. 또는 `subscriberCount`를 나중에 조회하도록 API 구조를 바꿀 수 있다.
- 두 구조 변경은 MID4-227에 포함하지 않고 별도 티켓에서 검증한다.
