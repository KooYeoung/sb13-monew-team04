# MID4-131 활동내역 k6 baseline

단일 사용자 활동내역 API의 RDB baseline 측정을 위한 k6 실행 스크립트입니다.
이 작업은 스크립트 구성만 다루며, 실제 측정 결과 기록은 MID4-132에서 진행합니다.

## 전제

- 애플리케이션 서버가 실행 중이어야 합니다.
- 단일 활동내역 API endpoint가 구현되어 있어야 합니다.
- Docker로 k6를 실행하므로 로컬 k6 설치는 필요하지 않습니다.
- 성능 테스트 데이터는 MID4-130 seed 데이터를 사용합니다.

## 기본 설정

기준 사용자 ID 기본값은 MID4-130 seed 기준 사용자입니다.

```text
00000001-0000-4000-8000-000000000001
```

활동내역 API 경로는 아래 환경변수로 바꿀 수 있습니다.

```properties
K6_ACTIVITY_HISTORY_PATH_TEMPLATE=/api/user-activities/{userId}
```

`{userId}`는 `K6_TARGET_USER_ID` 값으로 치환됩니다. 사용자 ID를 헤더로 받는 API라면 path template에는 실제 endpoint만 넣고, 기본 헤더 `Monew-Request-User-ID`를 그대로 사용합니다.

## Smoke 실행

```powershell
docker compose -f compose.k6.yaml config
docker compose -f compose.k6.yaml run --rm -e K6_SCENARIO=smoke k6
```

서버 포트나 endpoint가 다르면 다음처럼 바꿉니다.

```powershell
docker compose -f compose.k6.yaml run --rm `
  -e K6_SCENARIO=smoke `
  -e K6_BASE_URL=http://host.docker.internal:8080 `
  -e K6_ACTIVITY_HISTORY_PATH_TEMPLATE=/api/user-activities/{userId} `
  -e K6_TARGET_USER_ID=00000001-0000-4000-8000-000000000001 `
  k6
```

## Baseline 실행

Baseline 시나리오는 `constant-arrival-rate` 기준으로 요청 도착률을 맞추므로 `K6_SLEEP_SECONDS`를 적용하지 않습니다.

```powershell
docker compose -f compose.k6.yaml run --rm `
  -e K6_SCENARIO=baseline `
  -e K6_BASELINE_RATE=20 `
  -e K6_BASELINE_DURATION=1m `
  k6
```

macOS, Linux, Git Bash에서는 줄바꿈 문자를 `\`로 바꿔 실행합니다.

```bash
docker compose -f compose.k6.yaml run --rm \
  -e K6_SCENARIO=baseline \
  -e K6_BASELINE_RATE=20 \
  -e K6_BASELINE_DURATION=1m \
  k6
```

## 주요 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `K6_BASE_URL` | `http://host.docker.internal:8080` | 애플리케이션 서버 URL |
| `K6_ACTIVITY_HISTORY_PATH_TEMPLATE` | `/api/user-activities/{userId}` | 활동내역 API path 또는 전체 URL |
| `K6_TARGET_USER_ID` | `00000001-0000-4000-8000-000000000001` | 측정 대상 사용자 ID |
| `K6_USER_ID_HEADER_NAME` | `Monew-Request-User-ID` | 사용자 ID 전달 헤더명 |
| `K6_EXPECTED_STATUS` | `200` | 성공으로 판단할 HTTP 상태 코드, 100-599 정수 중 응답 본문이 가능한 값만 허용 |
| `K6_SCENARIO` | `smoke` | `smoke` 또는 `baseline` |
| `K6_SMOKE_VUS` | `1` | smoke 실행 VU 수 |
| `K6_SMOKE_ITERATIONS` | `1` | smoke 전체 iteration 수 |
| `K6_SMOKE_MAX_DURATION` | `30s` | smoke 최대 실행 시간 |
| `K6_BASELINE_RATE` | `20` | baseline 요청 도착률, 단위는 `K6_BASELINE_TIME_UNIT` 기준 |
| `K6_BASELINE_TIME_UNIT` | `1s` | baseline rate 기준 시간 단위 |
| `K6_BASELINE_DURATION` | `1m` | baseline 지속 시간 |
| `K6_BASELINE_PRE_ALLOCATED_VUS` | `20` | 사전 할당 VU 수 |
| `K6_BASELINE_MAX_VUS` | `100` | 최대 VU 수 |
| `K6_HTTP_REQ_FAILED_RATE_THRESHOLD` | `0.01` | HTTP 실패율 임계값 |
| `K6_HTTP_REQ_DURATION_P95_THRESHOLD` | `1000` | p95 응답 시간 임계값, ms |
| `K6_HTTP_REQ_DURATION_P99_THRESHOLD` | `2000` | p99 응답 시간 임계값, ms |
| `K6_CHECK_RATE_THRESHOLD` | `0.99` | check 성공률 임계값 |
| `K6_DROPPED_ITERATIONS_COUNT_THRESHOLD` | `1` | `dropped_iterations` count 미만 임계값, 기본값은 0건만 통과 |
| `K6_SLEEP_SECONDS` | `1` | smoke iteration 사이 대기 시간, `0`이면 대기하지 않음 |
| `K6_SUMMARY_PATH` | path 기반 자동 생성 | summary JSON 저장 경로를 직접 지정할 때 사용 |
| `K6_AUTHORIZATION` | 없음 | 인증이 필요할 때 Authorization 헤더 값 |

## 결과

실행 결과는 stdout에 아래 항목을 출력합니다.

- p95
- p99
- error rate
- RPS
- dropped iterations

summary JSON은 기본적으로 아래 경로에 저장됩니다.

```text
scripts/performance/activity-history/k6/results/activity-history-summary.json
```

`K6_SUMMARY_PATH`를 지정하지 않으면 `K6_ACTIVITY_HISTORY_PATH_TEMPLATE` 값으로 파일명을 정합니다.
기본 활동내역 API는 기존 파일명인 `activity-history-summary.json`을 사용하고, 다른 endpoint는 path 기반 파일명을 사용합니다.

예를 들어 `/api/articles`는 `articles-summary.json`, `/api/interests/{interestId}/subscriptions`는 `interests-subscriptions-summary.json`으로 저장합니다.
파일명을 고정해야 하면 다음처럼 직접 지정합니다.

```powershell
docker compose -f compose.k6.yaml run --rm `
  -e K6_SUMMARY_PATH=/results/custom-summary.json `
  k6
```

`results` 디렉터리는 Git에 포함하지 않습니다.
