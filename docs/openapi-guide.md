# OpenAPI 문서화 가이드

## 접근 경로

Swagger UI는 실행 profile의 애플리케이션 서버 URL을 기준으로 접근합니다.

```text
{server-url}/swagger-ui.html
```

OpenAPI JSON 문서는 아래 경로에서 확인합니다.

```text
{server-url}/v3/api-docs
```

`dev` profile은 yaml의 `server.port` 기준으로 localhost 문서 서버 URL을 생성합니다.

```text
http://localhost:${server.port:8080}
```

`prod` profile은 배포 환경의 외부 URL을 `MONEW_OPENAPI_SERVER_URL` 환경변수로 받습니다.

```text
${MONEW_OPENAPI_SERVER_URL}
```

## 기본 설정

OpenAPI 기본 정보는 `OpenApiConfig`에서 관리합니다.

- 문서 제목: `Monew API 문서`
- 버전: `v1`
- dev 서버 설명: `로컬 개발 서버`
- prod 서버 설명: `운영 배포 서버`
- prod 서버 URL: `MONEW_OPENAPI_SERVER_URL` 설정값

## API 문서화 기준

API 구현자는 컨트롤러 단위에서 필요한 설명을 최소한으로 추가합니다.

- API 동작 설명이 필요한 경우 `@Operation`을 사용합니다.
- 응답 상태나 오류 응답을 명확히 해야 하는 경우 `@ApiResponse`를 사용합니다.
- 요청 파라미터 의미가 코드만으로 명확하지 않은 경우 `@Parameter` 또는 schema 설명을 추가합니다.
- 공통 오류 응답은 공통 예외 처리 기준과 맞춰 문서화합니다.

## 확인 방법

로컬에서 애플리케이션을 실행한 뒤 Swagger UI와 OpenAPI JSON 경로가 열리는지 확인합니다.

```powershell
.\gradlew.bat bootRun
```

기본 포트 기준 접근 경로는 아래와 같습니다.

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```
