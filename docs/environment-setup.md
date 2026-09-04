# 환경 설정 방법

## 1. 로컬 환경 파일 생성

공유 예시 파일을 복사해서 개인 로컬 환경 파일을 만듭니다.

```powershell
Copy-Item .env.example .env.dev
```

`.env.dev`에는 개인 PC에서 사용할 DB 포트, 계정, 비밀번호 같은 값을 입력합니다. 이 파일은 Git에 올리지 않습니다.

## 2. 개발 DB 설정

`dev` profile은 `.env.dev`를 읽고 `MONEW_DB_*` 값을 PostgreSQL 설정에 사용합니다.

```properties
MONEW_DB_HOST=localhost
MONEW_DB_PORT=5432
MONEW_DB_NAME=monew
MONEW_DB_USERNAME=monew
MONEW_DB_PASSWORD=change-me
```

필요하면 팀원별로 포트나 비밀번호를 바꿔 사용할 수 있습니다.

## 3. 테스트 DB 설정

테스트는 `test` profile을 사용합니다.

`src/test/resources/application.properties`에서 test profile을 활성화하고, `src/test/resources/application-test.yaml`에서 H2 in-memory DB를 사용합니다.

테스트 환경에서는 Docker Compose와 Flyway를 실행하지 않습니다.

## 4. Docker Compose 자동 실행

개발 환경에서는 Spring Boot가 `compose.yaml`을 기준으로 PostgreSQL 컨테이너를 자동 실행합니다.

```properties
MONEW_DOCKER_COMPOSE_ENABLED=true
```

Spring Boot가 Docker Compose를 실행할 때 `.env.dev`를 env file로 전달하므로, Compose에서도 같은 DB 값을 사용합니다.

## 5. Docker를 사용하지 않는 경우

로컬에 직접 PostgreSQL을 설치해서 쓰거나 Docker를 쓰지 않는 팀원은 `.env.dev`에서 아래처럼 설정합니다.

```properties
MONEW_DOCKER_COMPOSE_ENABLED=false
```

이 경우 PostgreSQL은 직접 실행해 두어야 합니다.

## 6. 수동 실행 명령

필요하면 Docker Compose를 직접 실행할 수 있습니다.

```powershell
docker compose --env-file .env.dev up -d postgres
```

중지할 때는 아래 명령을 사용합니다.

```powershell
docker compose down
```

## 7. MongoDB Read Model 로컬 설정

MongoDB Read Model은 기본적으로 비활성화되어 있습니다. 후속 MongoDB 작업을 실행할 때만 `.env.dev`에서 활성화합니다.

이 환경 준비는 MongoDB Read Model의 운영 적용 결정을 의미하지 않습니다. 현재 활동내역 API는 계속 RDB를 사용하며, 적용 판단과 미구현 범위는 [MID4-96 MongoDB/Redis 적용 여부 판단 기록](mid4-96-mongodb-decision-record/README.md)에서 관리합니다.

MongoDB 컨테이너는 관리 전용 root 계정과 애플리케이션 전용 계정을 분리합니다. 애플리케이션 계정에는 `MONEW_MONGODB_DATABASE`에 대한 `readWrite` 권한만 부여됩니다. 아래의 계정명과 비밀번호는 개인 로컬 값으로 교체하고, URI의 비밀번호에 특수 문자가 있으면 URL encoding합니다.

```properties
MONEW_MONGODB_ENABLED=true
MONEW_MONGODB_INITIALIZE_INDEXES=true
MONEW_MONGODB_WORKER_ENABLED=false
MONEW_MONGODB_WORKER_FIXED_DELAY_MS=1000
MONEW_MONGODB_WORKER_BATCH_SIZE=100
MONEW_MONGODB_WORKER_CLAIM_LEASE=5m
MONEW_MONGODB_WORKER_HEARTBEAT_INTERVAL=1m
MONEW_MONGODB_BACKFILL_ENABLED=false
# MONEW_MONGODB_BACKFILL_RUN_ID=<실행마다-새-UUID>
MONEW_MONGODB_BACKFILL_FIXED_DELAY_MS=1000
MONEW_MONGODB_BACKFILL_BATCH_SIZE=100
MONEW_MONGODB_BACKFILL_CLAIM_LEASE=5m
MONEW_MONGODB_BACKFILL_HEARTBEAT_INTERVAL=1m
MONEW_MONGODB_PORT=27017
MONEW_MONGODB_DATABASE=monew
MONEW_MONGODB_ROOT_USERNAME=<관리자-계정>
MONEW_MONGODB_ROOT_PASSWORD=<관리자-비밀번호>
MONEW_MONGODB_APP_USERNAME=<애플리케이션-계정>
MONEW_MONGODB_APP_PASSWORD=<애플리케이션-비밀번호>
MONEW_MONGODB_URI=mongodb://<애플리케이션-계정>:<URL-인코딩된-애플리케이션-비밀번호>@localhost:27017/monew?authSource=monew
```

root 계정은 `admin` database에서 초기화와 관리 작업에만 사용합니다. 애플리케이션과 `MONEW_MONGODB_URI`에서는 root 계정을 사용하지 않습니다. 로컬 Compose 포트는 `127.0.0.1`에만 열리며, 원격 또는 운영 MongoDB에 연결할 때는 서버 인증서를 검증하는 TLS URI를 사용합니다.

초기 투영은 `MONEW_MONGODB_ENABLED`, `MONEW_MONGODB_WORKER_ENABLED`, `MONEW_MONGODB_BACKFILL_ENABLED`가 모두 `true`이고 유효한 `MONEW_MONGODB_BACKFILL_RUN_ID`가 있을 때만 실행됩니다. 재개·검증 절차는 [초기 데이터 투영 및 정합성 검증](mid4-96-mongodb-decision-record/09-initial-projection-and-reconciliation.md)을 따릅니다.

MongoDB만 수동 실행할 때는 다음 명령을 사용합니다.

```powershell
docker compose --env-file .env.dev up -d mongodb
docker compose --env-file .env.dev ps mongodb
```

`MONEW_MONGODB_ENABLED=true`이고 `MONEW_MONGODB_INITIALIZE_INDEXES=true`이면 애플리케이션 시작 시 `activity_histories`와 세 snapshot 컬렉션의 필수 인덱스를 멱등하게 확인하고 생성합니다. 테스트 profile에서는 MongoDB와 인덱스 초기화를 비활성화해 H2 기반 테스트를 유지합니다.

Outbox worker는 `MONEW_MONGODB_ENABLED=true`와 `MONEW_MONGODB_WORKER_ENABLED=true`가 모두 설정된 경우에만 실행됩니다. 기본값은 비활성화입니다. 여러 애플리케이션 인스턴스에서 활성화하면 각 인스턴스가 PostgreSQL `FOR UPDATE SKIP LOCKED`로 서로 겹치지 않는 이벤트 row batch를 claim하고 병렬 처리합니다. 서로 다른 batch에 같은 target의 이벤트가 포함될 수 있지만 MongoDB 쓰기는 전역 `projectionVersion` CAS로 순서를 보호합니다.

`MONEW_MONGODB_WORKER_FIXED_DELAY_MS`는 앞선 polling 실행이 끝난 뒤 다음 실행까지 기다리는 fixed delay이고, `MONEW_MONGODB_WORKER_BATCH_SIZE`는 한 번에 claim할 최대 이벤트 수입니다. claim lease 기본값은 5분이며 1분 간격 heartbeat로 연장합니다. heartbeat 간격은 lease보다 짧아야 합니다. 처리 중단이나 인스턴스 종료로 heartbeat가 멈추면 lease 만료 후 다른 인스턴스가 이벤트를 회수합니다. worker는 요청과 분리되어 실행되지만 내부 RDB 조회와 MongoDB 쓰기는 blocking 방식으로 순차 처리합니다. 테스트 profile에서는 worker를 비활성화합니다.

```text
인스턴스 A -> E1(target=C1, projectionVersion=41) claim
인스턴스 B -> E2(target=C1, projectionVersion=42) claim
인스턴스 B -> V42를 먼저 MongoDB에 반영
인스턴스 A -> 저장 version < 41 조건이 불일치해 stale 성공(no-op)
```

`projectionVersion`은 요청 트랜잭션이 singleton `outbox_projection_clock` row를 `PESSIMISTIC_WRITE`로 잠근 뒤 증가시킵니다. 잠금은 원본 변경과 Outbox 저장이 commit될 때까지 유지되므로 발급 순서와 commit 순서가 일치하며, 요청 쓰기가 이 전역 잠금에서 잠시 대기할 수 있다는 비용이 있습니다. MongoDB는 natural key로 계산한 SHA-256 `_id`와 저장 버전이 없거나 더 작은 경우만 갱신합니다. 물리삭제도 식별 필드를 지운 tombstone을 남겨 과거 이벤트의 재생성을 차단합니다. 상세 내용은 [Outbox worker 동시성 설명](./mid4-96-mongodb-decision-record/04-outbox-design.md#다중-worker-실행과-projection-version-cas)을 따릅니다.

이 스키마를 적용하면 기존 MongoDB 문서는 결정적 `_id`, `projectionVersion`, `tombstone` 계약과 호환되지 않습니다. 기존의 같은 이름인 non-partial natural-key 인덱스도 partial unique 인덱스로 제자리 변경할 수 없어, 그대로 두면 시작 시 `IndexOptionsConflict`가 발생합니다. 현재 Read Model은 기본 비활성화이고 운영 조회 경로에 사용하지 않으므로 온라인 변환이나 애플리케이션 시작 시 자동 인덱스 삭제는 제공하지 않습니다. 로컬에서는 아래 절차로 `mongodb-data` 볼륨을 재생성해 문서와 인덱스를 함께 초기화한 뒤 Read Model을 다시 투영합니다.

CAS 도입 전에 생성된 Outbox 삭제 payload에는 삭제 전에만 알 수 있는 activity key와 자식 snapshot 영향 범위가 없습니다. 현재 저장소에는 pre-CAS Outbox를 안전하게 projection하는 처리 경로가 없고, 물리 삭제 후에는 RDB에서 영향 범위를 복원할 수도 없습니다. 따라서 local/dev에서는 CAS worker를 활성화하기 전에 `projection_version=0`인 pre-CAS Outbox row와 MongoDB Read Model을 함께 초기화합니다. 이 데이터는 복구할 수 없으므로 필요한 기록을 먼저 백업해야 합니다.

```sql
DELETE FROM outbox_events WHERE projection_version = 0;
```

운영 환경에서는 별도 데이터 마이그레이션과 초기 투영 절차가 마련되기 전까지 worker와
MongoDB 조회 경로를 활성화하지 않습니다. 실제 초기 투영 시점에는 새 run-id를 지정하고
Outbox worker를 함께 활성화해야 투영 도중 발생한 신규 변경도 누락하지 않습니다.

| 단계 | `MONEW_MONGODB_ENABLED` | `MONEW_MONGODB_WORKER_ENABLED` | `MONEW_MONGODB_BACKFILL_ENABLED` | 조회 경로 |
| --- | --- | --- | --- | --- |
| 마이그레이션 준비 전 | `false` | `false` | `false` | RDB |
| 초기 투영 및 정합성 검증 중 | `true` | `true` | `true` | RDB |
| `COMPLETED` 확인 후 | `true` | `true` | `false` | MID4-139 전환 전까지 RDB |

완료 후에는 애플리케이션을 `MONEW_MONGODB_BACKFILL_ENABLED=false`로 재시작해 초기 투영
scheduler만 중지합니다. 실시간 변경을 계속 반영하려면 Outbox worker는 활성화 상태로
유지합니다.

### MongoDB 계정 변경과 개발 볼륨 재생성

MongoDB 공식 이미지의 계정과 `/docker-entrypoint-initdb.d` 스크립트는 빈 `mongodb-data` 볼륨을 처음 초기화할 때만 적용됩니다. 기존 볼륨을 유지하면서 `.env.dev`의 비밀번호만 바꾸면 database 계정은 변경되지 않아 healthcheck와 애플리케이션 인증이 실패합니다.

기존 데이터를 유지해야 하면 현재 root 계정으로 `admin` database에 인증합니다. `--password` 뒤에 값을 쓰지 않으면 `mongosh`가 비밀번호를 대화형으로 입력받습니다.

```powershell
docker compose --env-file .env.dev exec mongodb mongosh --username "<현재-root-계정>" --authenticationDatabase admin --password
```

접속한 `mongosh`에서 `passwordPrompt()`를 사용해 root 계정과 애플리케이션 계정의 비밀번호를 각각 변경합니다. 비밀번호를 명령이나 shell history에 직접 입력하지 않습니다.

```javascript
db.getSiblingDB("admin").changeUserPassword("<root-계정>", passwordPrompt())
db.getSiblingDB("monew").changeUserPassword("<애플리케이션-계정>", passwordPrompt())
```

그 다음 `.env.dev`의 `MONEW_MONGODB_ROOT_PASSWORD`, `MONEW_MONGODB_APP_PASSWORD`, `MONEW_MONGODB_URI`를 같은 값으로 갱신하고 새 환경변수를 읽도록 컨테이너를 다시 생성합니다.

```powershell
docker compose --env-file .env.dev up -d --force-recreate mongodb
docker compose --env-file .env.dev ps mongodb
```

개발 데이터를 폐기해도 되면 다음 명령으로 MongoDB 컨테이너와 `mongodb-data` 볼륨만 제거한 뒤 새 자격 증명으로 초기화합니다. 이 작업은 로컬 MongoDB 데이터를 복구할 수 없게 삭제합니다.

```powershell
docker compose --env-file .env.dev down --volumes mongodb
docker compose --env-file .env.dev up -d mongodb
docker compose --env-file .env.dev ps mongodb
```

## 8. NAVER API 설정

NAVER 뉴스 검색 API를 사용하려면 NAVER API Hub에서 검색 API 사용 권한을 준비합니다.

- 공식 문서: https://api.ncloud-docs.com/docs/naver-api-hub-search-news
- 요청 URL: `https://naverapihub.apigw.ntruss.com/search/v1/news`
- 인증 헤더: `X-NCP-APIGW-API-KEY-ID`, `X-NCP-APIGW-API-KEY`
- 요청 파라미터: `query`, `display`, `start`, `sort`, `format`

발급받은 인증 값은 `.env.dev`에만 입력합니다. `.env.dev`는 개인 로컬 설정 파일이므로 Git에 올리지 않습니다.

```properties
MONEW_NAVER_CLIENT_ID=발급받은-api-hub-client-id
MONEW_NAVER_CLIENT_SECRET=발급받은-api-hub-client-secret
MONEW_NAVER_CONNECT_TIMEOUT=3s
MONEW_NAVER_READ_TIMEOUT=5s
```

공유 예시 파일인 `.env.example`에는 실제 인증 값을 넣지 않고 빈 값만 유지합니다.

애플리케이션에서는 `application.yaml`의 `monew.news.naver.*` 설정을 통해 아래 값이 바인딩됩니다.

```yaml
monew:
  news:
    naver:
      base-url: https://naverapihub.apigw.ntruss.com
      path: /search/v1/news
      client-id: ${MONEW_NAVER_CLIENT_ID:}
      client-secret: ${MONEW_NAVER_CLIENT_SECRET:}
      connect-timeout: ${MONEW_NAVER_CONNECT_TIMEOUT:3s}
      read-timeout: ${MONEW_NAVER_READ_TIMEOUT:5s}
```

timeout 값은 선택 설정이며 지정하지 않으면 연결 timeout은 3초, 읽기 timeout은 5초를 사용합니다.

`display`는 최대 100, `start`는 최대 1000까지 사용할 수 있습니다. `sort`는 정확도순 `sim` 또는 날짜순 `date`를 사용합니다. JSON 응답은 `format=json` 요청 파라미터로 명시합니다.

## 9. 외부 호출 smoke 테스트

기본 테스트는 외부 네트워크 호출을 실행하지 않습니다. 외부 호출 검증이 필요한 테스트는 `@Tag("external")`로 분리하고, 기본 `test` task에서는 제외합니다.

```powershell
.\gradlew.bat test
```

RSS 실제 endpoint 호출은 별도 task로 실행합니다.

```powershell
.\gradlew.bat --no-daemon rssExternalTest
```

NAVER 뉴스 검색 API 실제 호출은 별도 task로 실행합니다.

```powershell
.\gradlew.bat --no-daemon naverExternalTest
```

`naverExternalTest`는 먼저 `MONEW_NAVER_CLIENT_ID`, `MONEW_NAVER_CLIENT_SECRET` 환경변수를 확인하고, 없으면 로컬 `.env.dev` 값을 읽습니다. 두 값은 NAVER API Hub의 Client ID와 Client Secret입니다. 인증 값이 없으면 테스트를 실패시키지 않고 skip합니다.
