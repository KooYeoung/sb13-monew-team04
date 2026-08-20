# AGENTS.md

## 프로젝트 기준

- 이 저장소의 공통 작업 기준은 Jira, GitHub PR, commit 이력을 같은 Jira 티켓 번호로 연결하는 것이다.
- 민감한 값은 commit하지 않는다. 개인 환경 파일은 `.env.dev`를 사용하고, 공유 가능한 기본값은 `.env.example`에만 둔다.
- Spring profile은 `dev`, `prod`, `test`를 기준으로 한다. 테스트는 기본적으로 `test` profile과 H2 database를 사용한다.
- 기존 코드 스타일과 구조를 우선한다. 요청 범위 밖의 리팩터링은 하지 않는다.

## 검증 명령

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

Mac, Linux, Git Bash:

```bash
./gradlew test
./gradlew clean build
```

## Jira, Branch, Commit, PR 규칙

- 모든 작업은 Jira 티켓을 기준으로 관리한다.
- 하나의 branch와 PR은 기본적으로 하나의 Jira 티켓만 대표로 연결한다.
- 작업 범위가 커지면 먼저 Jira 하위 티켓을 나눈 뒤 branch와 PR을 분리한다.
- `main`과 `develop`에는 직접 push하지 않는다.
- `main` 대상 PR은 같은 저장소의 `develop` 브랜치에서만 생성한다.
- `develop` 대상 PR은 `main` 외의 작업 브랜치에서 생성한다.
- branch 이름은 `<type>/MID4-번호-간단한-설명` 형식을 사용한다.
- 허용 type은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore`이다.
- commit 메시지는 `<type>: MID4-번호 변경 내용` 형식을 사용한다.
- PR 제목은 `MID4-번호 작업 요약` 형식을 사용한다.
- PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 사용하고, 템플릿 섹션을 임의로 누락하지 않는다.
- PR 본문에는 대표 Jira 번호, 작업 내용, 확인 방법, 확인 사항을 실제 작업 기준으로 작성한다.
- PR 본문에는 아래 Jira 필드를 유지하고 실제 티켓 번호를 입력한다.

```text
- Jira: MID4-번호
```
- CodeRabbit은 보조 리뷰로 사용하고, 관련 도메인 담당자의 사람 리뷰를 거친 뒤 병합한다.
- PR은 Squash merge를 사용하고, 병합 후 작업 브랜치를 삭제한다.

## PR 흐름

- 작업 branch에서 `develop`으로 PR을 열면 build와 test가 통과해야 한다.
- `develop`에서 `main`으로 PR을 열 때만 운영 반영 PR로 취급한다.
- `main` 대상 PR이 `develop`에서 출발하지 않으면 merge할 수 없도록 실패해야 한다.
- PR에는 구현 내용, 확인 방법, 로컬 실행 또는 테스트 결과를 남긴다.
- PR에 민감한 값, 개인 환경 파일, 불필요한 IDE/빌드 산출물이 포함되지 않았는지 확인한다.
- 충돌이 발생하면 PR 작성자가 최신 `develop`을 작업 브랜치에 반영해 해결하고 테스트를 다시 실행한다.

## Git Hook 적용

- commit template과 commit-msg hook은 `docs/git-commit-convention.md`와 `scripts/setup-git.*`를 따른다.
- 현재 단계에서는 다른 팀원이 작업 중인 저장소에서 대신 `git config --local`을 실행하지 않는다.
- 실제 clone/init된 Git 저장소 작업이 완료된 뒤 전달받으면 hook 적용 여부와 commit-msg 검증을 별도로 수행한다.

## AI 작업 기준

- 사용자가 Jira 기반 branch, commit, PR 작성을 요구하면 위 형식을 먼저 확인한다.
- Jira 번호가 없거나 여러 개가 섞이면 작업 전 명확히 한다.
- 파일을 수정할 때는 관련 범위만 바꾸고, 검증 결과를 함께 보고한다.
