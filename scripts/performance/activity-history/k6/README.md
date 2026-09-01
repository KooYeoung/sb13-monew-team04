# 활동내역 k6 성능 테스트

단일 활동내역 API의 RDB baseline 측정과 MongoDB Read Model 적용 전후 비교를 위한 k6 실행 스크립트입니다.
MID4-206에서는 문서에 정의된 VU 기준 시나리오를 기본으로 맞추고, RPS 기준 측정은 별도 `throughput` 시나리오로 유지합니다.

## 전제

- 애플리케이션 서버가 실행 중이어야 합니다.
- 대상 API는 기본적으로 `GET /api/user-activities/{userId}`입니다.
- Docker로 k6를 실행하므로 로컬 k6 설치는 필요하지 않습니다.
- 성능 테스트 데이터는 기존 activity-history seed 데이터를 사용합니다.
- MongoDB 비교 시 RDB/MongoDB 전환은 애플리케이션 설정 또는 배포 상태로 맞춘 뒤 같은 k6 조건을 각각 실행합니다.

## 시나리오

| 시나리오 | executor | 기본 부하 | 기본 시간 | 목적 |
| --- | --- | --- | --- | --- |
| `smoke` | `constant-vus` | 1 VU | 1분 | 스크립트, 인증, 응답 검증 |
| `baseline` | `constant-vus` | 20 VU | 5분 | 기본 성능 비교 |
| `average` | `constant-vus` | 50 VU | 10분 | 일반적인 동시 사용 상황 가정 |
| `high-load` | `constant-vus` | 100 VU | 10분 | 병목 발생 여부 확인 |
| `stress` | `ramping-vus` | 50 -> 100 -> 200 -> 400 VU | 각 3분 | 성능이 무너지는 지점 확인 |
| `throughput` | `constant-arrival-rate` | 50 rps | 1분 | RPS 기준 처리량 확인 |

VU 기준 시나리오는 iteration 사이에 `K6_SLEEP_SECONDS`를 적용합니다. `throughput`은 요청 도착률을 k6가 제어하므로 sleep을 적용하지 않습니다.

기존 MID4-179 결과 문서에서는 `baseline`이 RPS 측정 의미로 사용된 기록이 있습니다. MID4-206 기준에서는 `baseline`을 20 VU 기본 비교로 사용하고, RPS 기준 측정은 `throughput`으로 분리합니다.

## 기본 실행

```powershell
docker compose -f compose.k6.yaml config
docker compose -f compose.k6.yaml run --rm -e K6_SCENARIO=smoke k6
```

여러 사용자 ID를 순환하려면 `K6_TARGET_USER_IDS`와 `K6_USER_PICK_STRATEGY`를 지정합니다.

```powershell
docker compose -f compose.k6.yaml run --rm `
  -e K6_SCENARIO=smoke `
  -e K6_TARGET_USER_IDS=00000001-0000-4000-8000-000000000001,00000001-0000-4000-8000-000000000002 `
  -e K6_USER_PICK_STRATEGY=round-robin `
  k6
```

## VU 기준 비교 실행

비교 실행 헬퍼는 같은 조건의 summary와 raw log 파일명을 variant별로 분리합니다. `-Variant` 값은 결과 구분용 메타데이터이며, 실제 RDB/MongoDB 전환은 애플리케이션 실행 설정 또는 배포 상태에서 먼저 맞춥니다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario baseline
```

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario average
```

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario high-load
```

MongoDB Read Model 기준 애플리케이션을 실행한 뒤 `-Variant mongo`로 같은 시나리오를 반복합니다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant mongo `
  -Scenario stress `
  -MongoContainer sb13-monew-team04-mongodb-1
```

## RPS 기준 보강 실행

문서의 VU 기준 측정과 별도로 RPS 기준 처리량을 확인할 때만 `throughput`을 사용합니다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario throughput `
  -Rates 50,100,150,200,250 `
  -Duration 1m
```

MongoDB Read Model 기준 애플리케이션을 실행한 뒤 같은 조건으로 반복합니다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant mongo `
  -Scenario throughput `
  -Rates 50,100,150,200,250 `
  -Duration 1m `
  -MongoContainer sb13-monew-team04-mongodb-1
```

macOS, Linux, Git Bash에서는 줄바꿈 문자를 `\`로 바꿔 실행합니다.

## 주요 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `K6_BASE_URL` | `http://host.docker.internal:8080` | 애플리케이션 서버 URL |
| `K6_ACTIVITY_HISTORY_PATH_TEMPLATE` | `/api/user-activities/{userId}` | 활동내역 API path 또는 전체 URL |
| `K6_VARIANT` | `rdb` | 결과 구분용 variant, `rdb` 또는 `mongo` |
| `K6_TICKET` | `MID4-206` | summary에 기록할 Jira 티켓 번호 |
| `K6_RUN_INDEX` | `1` | 같은 조건 반복 측정의 실행 순번 |
| `K6_TARGET_USER_ID` | `00000001-0000-4000-8000-000000000001` | 단일 측정 대상 사용자 ID |
| `K6_TARGET_USER_IDS` | 없음 | 콤마로 구분한 측정 대상 사용자 ID 목록 |
| `K6_USER_PICK_STRATEGY` | `single` | 사용자 선택 방식, `single`, `round-robin`, `random` |
| `K6_USER_ID_HEADER_NAME` | `Monew-Request-User-ID` | 사용자 ID 전달 헤더명 |
| `K6_EXPECTED_STATUS` | `200` | 성공으로 판단할 HTTP 상태 코드 |
| `K6_SCENARIO` | `smoke` | `smoke`, `baseline`, `average`, `high-load`, `stress`, `throughput` |
| `K6_SMOKE_VUS` | `1` | smoke VU 수, 1~5 VU 범위에서 사용 |
| `K6_SMOKE_DURATION` | `1m` | smoke 지속 시간 |
| `K6_BASELINE_VUS` | `20` | baseline VU 수, 10~20 VU 범위에서 사용 |
| `K6_BASELINE_DURATION` | `5m` | baseline 지속 시간, 3~5분 범위에서 사용 |
| `K6_AVERAGE_VUS` | `50` | average load VU 수 |
| `K6_AVERAGE_DURATION` | `10m` | average load 지속 시간 |
| `K6_HIGH_LOAD_VUS` | `100` | high load VU 수 |
| `K6_HIGH_LOAD_DURATION` | `10m` | high load 지속 시간 |
| `K6_STRESS_START_VUS` | `0` | stress 시작 VU 수 |
| `K6_STRESS_STAGES` | `3m:50,3m:100,3m:200,3m:400` | `duration:targetVus` 형식의 stress 단계 |
| `K6_STRESS_GRACEFUL_RAMP_DOWN` | `30s` | stress 종료 시 graceful ramp down 시간 |
| `K6_THROUGHPUT_RATE` | `50` | throughput 요청 도착률 |
| `K6_THROUGHPUT_DURATION` | `1m` | throughput 지속 시간 |
| `K6_THROUGHPUT_PRE_ALLOCATED_VUS` | `500` | throughput 사전 할당 VU 수 |
| `K6_THROUGHPUT_MAX_VUS` | `500` | throughput 최대 VU 수 |
| `K6_SLEEP_SECONDS` | `1` | VU 기준 시나리오 iteration 사이 대기 시간 |
| `K6_HTTP_REQ_FAILED_RATE_THRESHOLD` | `0.01` | HTTP 실패율 임계값 |
| `K6_HTTP_REQ_DURATION_P95_THRESHOLD` | `200` | p95 응답 시간 임계값, ms |
| `K6_HTTP_REQ_DURATION_P99_THRESHOLD` | `500` | p99 응답 시간 임계값, ms |
| `K6_CHECK_RATE_THRESHOLD` | `0.99` | check 성공률 임계값 |
| `K6_DROPPED_ITERATIONS_COUNT_THRESHOLD` | `1` | `dropped_iterations` count 미만 임계값, 기본값은 0건만 통과 |
| `K6_SUMMARY_PATH` | path, variant, scenario 기반 자동 생성 | summary JSON 저장 경로를 직접 지정할 때 사용 |
| `K6_AUTHORIZATION` | 없음 | 인증이 필요할 때 Authorization 헤더 값 |

## 응답 검증

k6는 아래 항목을 공통으로 확인합니다.

- HTTP status가 `K6_EXPECTED_STATUS`와 일치합니다.
- 응답 body가 비어 있지 않습니다.
- 응답 body를 JSON으로 파싱할 수 있습니다.
- 사용자 활동내역의 `id`, `email`, `nickname`, `createdAt` 필드가 존재합니다.
- `subscriptions`, `comments`, `commentLikes`, `articleViews`가 배열입니다.

정렬과 삭제/취소 제외 조건의 상세 정확성은 기능 테스트에서 먼저 검증하고, k6에서는 성능 측정 중 기본 응답 구조가 깨지지 않는지만 확인합니다.

## 판단 기준

MongoDB 적용 타당성 판단 기준은 MID4-96 사전 문서의 임시 성공 기준을 따릅니다.

```text
http_req_failed < 1%
checks rate > 99%
dropped_iterations = 0
p95 < 200ms
p99 < 500ms
```

DB CPU와 커넥션 풀 대기는 k6가 직접 보장하는 지표가 아니므로 애플리케이션, DB, Docker stats 기준으로 함께 확인합니다. 수집 환경이 없으면 결과 문서에 미측정 사유를 남깁니다.

기존 MID4-179 처리량 한계 측정에서 사용한 `p95 < 1000ms`, `p99 < 2000ms`는 한계점 관찰용 참고 기준으로만 봅니다.

## 결과

summary JSON은 기본적으로 아래 형식으로 저장됩니다.

```text
scripts/performance/activity-history/k6/results/activity-history-{variant}-{scenario}-summary.json
```

비교 실행 헬퍼를 사용하면 아래 경로에 timestamp, scenario, VU 또는 rate가 포함된 결과가 저장됩니다.

```text
scripts/performance/activity-history/k6/results/mid4-206-mongodb-k6-compare/
```

다른 Jira 측정 결과는 `-ResultSet`으로 별도 디렉터리에 저장할 수 있습니다. 값에는 영문자, 숫자, `.`, `_`, `-`만 사용할 수 있습니다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Ticket MID4-227 `
  -Variant rdb `
  -Scenario throughput `
  -Rates 10,20,30,40,50 `
  -Duration 1m `
  -RepeatCount 3 `
  -StabilizationSeconds 30 `
  -ResultSet mid4-227-rdb-fanout
```

`-RepeatCount`는 같은 부하를 지정 횟수만큼 반복하고 summary에 `runIndex`를 기록합니다. `-StabilizationSeconds`는 첫 실행을 제외한 측정 사이의 대기 시간입니다.

`results` 디렉터리는 Git에 포함하지 않습니다.
