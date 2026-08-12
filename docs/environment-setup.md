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
