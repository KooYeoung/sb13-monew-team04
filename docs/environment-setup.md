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

## 7. NAVER API 설정

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

## 8. 외부 호출 smoke 테스트

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
