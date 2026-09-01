# MID4-227 RDB 활동 내역 성능 재측정

이 문서는 활동 내역 조회 방식을 바꾸기 전과 후의 속도를 비교한 결과다. 변경 전에는 관련 사용자의 `deleted_at`을 매번 확인했고, 변경 후에는 활동 데이터에 저장된 `visibility_status`를 바로 확인한다.

데이터 양, 요청량, 준비 실행은 모두 같게 맞췄다.

문서 구성은 [MID4-206 MongoDB 적용 대비 k6 비교](../mid4-206-mongodb-k6-compare.md)를 참고했다.

SQL 실행 과정은 [MID4-132 RDB 기준선](../mid4-132-activity-history-rdb-baseline/README.md)과 같이 조회 종류별로 나누어 기록했다.

## 한눈에 보는 결론

- 연결된 데이터가 많은 상황에서는 초당 처리할 수 있는 요청의 통과 상한이 `20 rps`에서 `150 rps`로 높아졌다.
- 같은 상황을 10분 동안 유지했을 때 `20 rps`의 p95/p99 응답 시간은 `283.76/317.46 ms`에서 `37.19/42.31 ms`로 줄었다.
- 삭제·비활성 데이터가 많은 상황에서는 `50 rps`의 p95/p99 중앙값이 `206.67/304.45 ms`에서 `40.42/48.08 ms`로 줄었다. 10분 측정도 실패에서 통과로 바뀌었다.
- 가장 큰 개선 원인은 관련 사용자가 삭제되었는지 반복해서 조회하던 과정이 사라진 것이다.
- 일부 조회는 아직 비활성 데이터를 먼저 읽은 다음 제외한다. 이 경로는 활성 데이터만 담는 인덱스로 추가 개선할 수 있다.

변경 후 `200 rps`도 요청 누락 없이 처리했지만, p95가 `379.23 ms`로 기준인 `200 ms`를 넘었다. 따라서 이번 조건에서 확인한 최종 통과 상한은 `150 rps`다.

## 용어 설명

| 용어 | 이 문서에서의 의미 |
| --- | --- |
| fanout | 댓글의 좋아요처럼 한 항목에 연결된 데이터가 매우 많은 상황 |
| exclusion | 삭제되거나 비활성화된 데이터가 많이 섞여 있어 조회 중 제외해야 하는 상황 |
| RPS | 1초 동안 처리한 요청 수 |
| p95 / p99 | 전체 요청 중 느린 쪽까지 포함해 확인하는 응답 시간 지표 |
| dropped | 부하 도구가 정해진 시간에 시작하지 못한 요청 수 |
| 워밍업 | 초기 디스크 읽기나 애플리케이션 준비 시간이 결과에 섞이지 않도록 미리 실행하는 과정 |
| 실행계획 | DB가 어떤 순서로 데이터를 찾고 처리했는지 보여주는 기록 |
| Filter | 데이터를 찾은 뒤 조건에 맞지 않는 항목을 제외하는 작업 |
| heap | 실제 데이터가 저장된 테이블 본문 |
| 부분 인덱스 | `ACTIVE`처럼 특정 조건을 만족하는 데이터만 저장한 인덱스 |

## 비교 대상과 동일한 테스트 조건

| 항목 | 값 |
| --- | --- |
| Jira | `MID4-227` |
| 측정 시각 | 2026-08-31~2026-09-01 KST |
| before | `44dcf82` — 스키마가 호환되는 `deleted_at` 조회 구현 |
| after | `9c195bd` — `visibility_status` 조회 구현 |
| API | `GET /api/user-activities/{userId}` |
| 대상 사용자 | `00000001-0000-4000-8000-000000000001` |
| DB | PostgreSQL 16.14, 변경 버전과 추가 데이터 조건마다 분리된 DB 환경과 새 저장 공간 사용 |
| 데이터 | 각 변경 버전마다 `SEED_SCALE=10m`으로 1,000만 건을 새로 적재한 뒤 같은 추가 데이터와 `ANALYZE` 적용 |
| 애플리케이션 | Java 17, 개발용 설정(`dev`), 예약 작업 중지, 상세 SQL 로그 중지 |
| 부하 도구 | `grafana/k6:0.54.0`, 최대 가상 사용자 500명 |
| 1분 부하 | 초당 요청 수마다 3회 측정, 반복 사이 30초 안정화, 중앙값 비교 |
| 10분 지속 측정 | fanout 10/20 rps, exclusion 50 rps, 각 1회 |
| 통과 기준 | error `<1%`, checks `>99%`, dropped `0`, p95 `<200 ms`, p99 `<500 ms` |

before와 after는 같은 DB를 공유하지 않았다. 각 버전마다 새 DB를 만들고 아래 순서를 똑같이 실행해 이전 측정 데이터와 캐시 상태가 결과에 섞이지 않도록 했다.

1. 테스트 조합만 사용하는 DB 환경과 새 저장 공간(volume) 생성
2. 선택한 코드 버전(commit)의 애플리케이션으로 같은 DB 구조(Flyway schema) 적용
3. 같은 기본 데이터 생성 SQL로 1,000만 건 적재
4. 같은 추가 데이터(overlay)를 적용하고 DB가 데이터 분포를 파악하도록 `ANALYZE` 수행
5. 디버그 모드에서 활동 내역 API를 한 번 호출해 애플리케이션이 실제로 만든 SQL 저장
6. 측정에 불필요한 상세 로그를 끄고 애플리케이션 재시작
7. 가상 사용자 1명으로 1분간 미리 요청해 애플리케이션과 DB 워밍업
8. rate별 1분 측정 3회와 30초 안정화
9. 같은 요청량을 10분 동안 유지하는 지속 측정

## SQL 측정 방법

애플리케이션이 실제로 실행한 SQL은 API 디버그 로그에서 확인했다. 이 SQL에서 사용자 ID처럼 실행할 때 전달되는 값만 고정값으로 바꿔 측정했다. before와 after에는 같은 데이터, DB 통계와 PostgreSQL 설정을 사용했다.

1. 각 SQL을 3회 미리 실행해 테이블과 인덱스 데이터를 메모리에 올린다.
2. 실제 응답 시간 비교에 사용할 일반 SELECT를 각각 5회 측정한다.
3. DB가 데이터를 찾는 과정을 확인할 수 있도록 아래 EXPLAIN을 각각 5회 실행한다.
4. 특정 실행 순서가 결과에 영향을 주지 않도록 `before → after`와 `after → before` 순서를 번갈아 사용한다.
5. 5회 결과의 중앙값과 가장 가까운 실행계획을 대표 자료로 선택한다.

```sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING, FORMAT JSON)
SELECT ...;
```

EXPLAIN은 각 처리 단계의 시간과 행 수를 기록하므로 SQL만 실행할 때보다 시간이 더 걸릴 수 있다. 따라서 속도 비교에는 일반 SELECT의 중앙값을 사용했다.

EXPLAIN은 어느 단계에서 시간이 걸렸는지 찾는 근거로만 사용했다. PostgreSQL이 출력한 실행계획은 표로 바꾸지 않고 상세 문서의 코드 블록에 그대로 기록했다.

| 조회 종류 | before SQL | after SQL | 상세 실행계획 |
| --- | --- | --- | --- |
| 최근 작성 댓글 | [before](../../scripts/performance/activity-history/mid4-227/before/recent-comments.sql) | [after](../../scripts/performance/activity-history/mid4-227/after/recent-comments.sql) | [recent-comments.md](recent-comments.md) |
| 최근 좋아요 댓글 | [before](../../scripts/performance/activity-history/mid4-227/before/recent-comment-likes.sql) | [after](../../scripts/performance/activity-history/mid4-227/after/recent-comment-likes.sql) | [recent-liked-comments.md](recent-liked-comments.md) |
| 최근 조회 기사 | [before](../../scripts/performance/activity-history/mid4-227/before/recent-article-views.sql) | [after](../../scripts/performance/activity-history/mid4-227/after/recent-article-views.sql) | [recent-article-views.md](recent-article-views.md) |
| 구독 관심사 | [before](../../scripts/performance/activity-history/mid4-227/before/subscribed-interests.sql) | [after](../../scripts/performance/activity-history/mid4-227/after/subscribed-interests.sql) | [subscribed-interests.md](subscribed-interests.md) |

## 연결 데이터가 많은 경우(fanout) API 결과

한 항목에 연결된 데이터가 많을 때의 성능을 확인했다. 최근 댓글 10건에는 좋아요 10,000건, 최근 기사 10건에는 댓글 10,000건과 조회 100,000건, 구독 관심사 50건에는 구독 50,000건을 추가했다.

1분 측정값은 세 번 실행한 결과의 중앙값이다. 모든 실행에서 오류는 없었고 검증 항목도 모두 통과했다.

| 요청 부하 | before 실제 RPS | before p95/p99 ms | before dropped | after 실제 RPS | after p95/p99 ms | after dropped | 판정 변화 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 rps, 1분 | 10.95 | 179.76 / 199.04 | 0 | 11.00 | 38.96 / 44.45 | 0 | 통과 → 통과 |
| 20 rps, 1분 | 21.91 | 255.65 / 286.27 | 0 | 22.00 | 37.36 / 41.79 | 0 | 실패 → 통과 |
| 30 rps, 1분 | 26.90 | 16,036.33 / 16,355.55 | 0 | 32.99 | 36.64 / 41.49 | 0 | 실패 → 통과 |
| 40 rps, 1분 | 25.83 | 20,535.73 / 21,354.87 | 537 | 43.98 | 37.14 / 42.84 | 0 | 실패 → 통과 |
| 50 rps, 1분 | 26.39 | 21,338.73 / 22,434.58 | 1,157 | 54.98 | 37.66 / 42.88 | 0 | 실패 → 통과 |
| 100 rps, 1분 | 25.21 | 22,659.69 / 23,361.22 | 4,143 | 109.95 | 43.12 / 51.85 | 0 | 실패 → 통과 |
| 150 rps, 1분 | 25.64 | 21,427.28 / 22,906.92 | 7,129 | 164.87 | 53.94 / 64.60 | 0 | 실패 → 통과 |
| 200 rps, 1분 | 26.87 | 22,749.32 / 22,953.52 | 10,122 | 219.51 | 379.23 / 473.26 | 0 | 실패 → 실패(p95) |
| 10 rps, 10분 | 10.98 | 174.28 / 189.25 | 0 | 11.00 | 43.29 / 50.85 | 0 | 통과 → 통과 |
| 20 rps, 10분 | 21.97 | 283.76 / 317.46 | 0 | 22.00 | 37.19 / 42.31 | 0 | 실패 → 통과 |

## 삭제·비활성 데이터가 많은 경우(exclusion) API 결과

조회 과정에서 제외해야 할 데이터가 많을 때의 성능을 확인했다. 대상 사용자에게 최신 삭제 댓글 10,000건, 삭제 댓글의 좋아요 10,000건, 삭제 기사 조회 10,000건을 추가했다. 관심사마다 삭제 사용자의 구독도 1,000건씩, 총 50,000건 추가했다.

1분 측정값은 세 번 실행한 결과의 중앙값이다. 모든 실행에서 오류와 요청 누락은 없었고 검증 항목도 모두 통과했다.

| 요청 부하 | before 실제 RPS | before p95/p99 ms | after 실제 RPS | after p95/p99 ms | 판정 변화 |
| ---: | ---: | ---: | ---: | ---: | --- |
| 10 rps, 1분 | 11.00 | 107.81 / 129.41 | 11.01 | 39.61 / 47.79 | 통과 → 통과 |
| 20 rps, 1분 | 21.99 | 118.37 / 143.45 | 22.01 | 38.33 / 43.58 | 통과 → 통과 |
| 30 rps, 1분 | 32.95 | 128.55 / 154.65 | 33.00 | 38.70 / 45.72 | 통과 → 통과 |
| 40 rps, 1분 | 43.94 | 146.39 / 172.94 | 44.00 | 39.66 / 48.32 | 통과 → 통과 |
| 50 rps, 1분 | 54.90 | 206.67 / 304.45 | 54.97 | 40.42 / 48.08 | 실패 → 통과 |
| 50 rps, 10분 | 54.99 | 214.23 / 324.84 | 55.00 | 41.84 / 50.16 | 실패 → 통과 |

## SQL 중앙값 요약

일반 SELECT를 다섯 번 실행한 결과의 중앙값이며 단위는 ms다. 개선율이 양수이면 더 빨라졌고, 음수이면 더 느려졌다는 뜻이다.

| 데이터 조건 | SQL | before | after | 개선율 | 대표 실행계획 |
| --- | --- | ---: | ---: | ---: | --- |
| fanout | 최근 작성 댓글 | 10.236 | 3.111 | 69.6% | [상세](recent-comments.md#fanout-before) |
| fanout | 최근 좋아요 댓글 | 2.533 | 2.335 | 7.8% | [상세](recent-liked-comments.md#fanout-before) |
| fanout | 최근 조회 기사 | 90.610 | 15.484 | 82.9% | [상세](recent-article-views.md#fanout-before) |
| fanout | 구독 관심사 | 41.923 | 7.258 | 82.7% | [상세](subscribed-interests.md#fanout-before) |
| exclusion | 최근 작성 댓글 | 2.941 | 3.136 | -6.6% | [상세](recent-comments.md#exclusion-before) |
| exclusion | 최근 좋아요 댓글 | 10.783 | 3.476 | 67.8% | [상세](recent-liked-comments.md#exclusion-before) |
| exclusion | 최근 조회 기사 | 12.476 | 3.053 | 75.5% | [상세](recent-article-views.md#exclusion-before) |
| exclusion | 구독 관심사 | 62.931 | 19.494 | 69.0% | [상세](subscribed-interests.md#exclusion-before) |

## 남아 있는 문제와 개선 방향

- 변경 전에는 좋아요를 누른 사용자, 기사를 조회한 사용자, 댓글 작성자와 구독자가 삭제되었는지 한 명씩 반복해서 확인했다. 실행계획에서는 이 과정이 최대 100,030회 반복됐다.
- 변경 후에는 각 활동 데이터의 `visibility_status`만 확인하므로 이러한 사용자 반복 조회가 사라졌다. 이번 성능 개선의 가장 큰 원인이다.
- 최근 작성 댓글 조회는 비활성 댓글 10,000건을 인덱스에서 먼저 읽은 뒤 `Filter`에서 제외한다. 활성 댓글만 저장하는 부분 인덱스를 사용하면 이 불필요한 읽기를 줄일 수 있다.
- 구독 관심사 조회도 관심사마다 구독 1,011건을 읽은 다음 비활성 구독 1,000건을 제외한다. 이 경우에도 활성 구독만 저장하는 부분 인덱스를 후속으로 검증할 필요가 있다.
- 기사별 댓글·조회 수와 관심사별 구독자 수는 결과 한 건마다 다시 계산한다. 관련 데이터가 계속 늘어나면 인덱스만으로는 반복 계산 횟수를 줄일 수 없으므로 조회 SQL이나 API 응답 구조를 함께 검토해야 한다.

## 재현 명령

변경 전 코드와 비활성 데이터가 많은 조건을 측정하는 예시:

```powershell
.\scripts\performance\activity-history\mid4-227\run-api-compare.ps1 `
  -BuildLabel before `
  -Overlay exclusion `
  -AppJar C:\path\to\before.jar `
  -AppCommit 44dcf82
```

SQL 비교 예시:

```powershell
.\scripts\performance\activity-history\mid4-227\run-explain-compare.ps1 `
  -Overlay exclusion `
  -PgContainer monew-perf-227-sql-exclusion-postgres-1 `
  -WarmupRuns 3 `
  -MeasureRuns 5

.\scripts\performance\activity-history\mid4-227\render-explain-docs.ps1
```

렌더링 결과가 원본과 같은지는 `render-explain-docs.ps1 -Check`로 확인한다.

가공하지 않은 측정 결과, 실행계획, 애플리케이션 로그와 Docker 상태 정보는 `scripts/performance/activity-history/k6/results/` 아래에 저장한다. 이 파일들은 `.gitignore`로 제외한다.

Git에는 실행 SQL, 실행 스크립트와 결과를 정리한 문서만 포함한다.

## 완료 조건

- [x] 변경 전후 버전을 스키마가 호환되는 특정 commit으로 고정
- [x] 변경 버전과 추가 데이터 조건마다 새 DB를 만들고 같은 1,000만 건의 기본 데이터 적용
- [x] API 본 측정 전에 1분 동안 요청을 보내 애플리케이션과 DB 준비
- [x] 1분 부하를 각 3회 반복하고 중앙값 기록
- [x] 같은 요청량을 유지한 10분 측정 결과 기록
- [x] 애플리케이션이 실제로 실행한 SQL을 디버그 로그로 확인하고 측정 SQL 공개
- [x] SQL별 준비 실행 3회, 일반 조회 5회, 실행계획 확인 5회 수행
- [x] PostgreSQL 실행계획 원문을 문서의 코드 블록에 기록
- [x] 대표 실행계획에 디스크 읽기 시간과 디스크에서 읽은 블록이 없음을 확인
- [x] 가공하지 않은 JSON과 로그를 Git에서 제외
