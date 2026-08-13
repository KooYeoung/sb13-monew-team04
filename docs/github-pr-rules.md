# GitHub PR 작성 및 검증 규칙

## PR 작성 기준

PR은 하나의 대표 Jira 작업을 기준으로 작성합니다.

PR 본문에는 다음 Jira 필드를 유지합니다.

```markdown
- Jira: MID4-번호
- 대표 Jira 티켓 1개만 작성합니다.
```

PR 제목은 다음 형식을 사용합니다.

```plaintext
MID4-번호 작업 요약
```

PR 제목과 PR 본문에는 각각 대표 Jira 번호가 포함되어야 합니다.

작업 브랜치 이름은 다음 형식을 사용합니다.

```plaintext
<type>/MID4-번호-간단한-설명
```

## PR 템플릿 확인 항목

PR 작성자는 merge 전 다음 항목을 확인합니다.

* Jira 필드에 대표 Jira 번호를 작성했다.
* PR 제목이 `MID4-번호 작업 요약` 형식을 따른다.
* branch 이름이 팀 규칙을 따른다.
* 로컬 실행 또는 테스트를 확인했다.
* `develop` 대상 PR은 `develop-build-test` check 대상임을 확인했다.
* `main` 대상 PR은 `develop -> main` 흐름이며 `main-source-guard` check 대상임을 확인했다.
* 불필요한 파일과 민감정보가 포함되지 않았다.

## 리뷰와 병합 기준

CodeRabbit은 PR 리뷰를 보조하는 도구입니다.

CodeRabbit 의견은 코드 품질, 누락 가능성, 리뷰 효율을 높이기 위한 참고로 사용합니다. 단, CodeRabbit은 사람 리뷰, branch protection, ruleset, required status check를 대체하지 않습니다.

PR은 관련 도메인 담당자의 사람 리뷰를 거친 뒤 병합합니다.

PR 병합 방식은 Squash merge를 사용합니다. merge commit과 rebase merge는 사용하지 않는 것을 기준으로 합니다.

PR merge 후 작업 브랜치를 삭제합니다.

## 충돌 처리 기준

PR에서 충돌이 발생하면 PR 작성자가 최신 `develop`을 작업 브랜치에 반영해 충돌을 해결합니다.

충돌 해결 후 로컬 실행 또는 테스트를 다시 확인하고 PR에 결과를 남깁니다.

## required status check 기준

GitHub Actions check는 branch protection 또는 ruleset에서 required status check로 지정해야 실제 merge 차단 기준이 됩니다.

`main` branch에는 다음 check를 required status check로 지정합니다.

```plaintext
main-source-guard
```

`develop` branch에는 다음 check를 required status check로 지정합니다.

```plaintext
develop-build-test
```

workflow 파일만 추가된 상태에서는 check가 실행될 수는 있지만, required status check로 지정되지 않으면 실패한 PR의 merge를 GitHub 설정에서 차단하지 못할 수 있습니다.

## Jira 상태 동기화 GitHub Actions 설정

Jira PR 상태 동기화 workflow를 사용하려면 GitHub repository settings의 `Secrets and variables` > `Actions`에 값을 등록합니다.

필수 secrets는 다음과 같습니다.

```plaintext
JIRA_BASE_URL
JIRA_EMAIL
JIRA_API_TOKEN
```

variables는 기본값을 그대로 사용할 수 있지만, 팀 Jira 상태명이나 대상 branch가 달라지면 아래 값을 등록해 조정합니다.

```plaintext
JIRA_PROJECT_KEY=MID4
JIRA_TARGET_BASE_REF=develop
JIRA_STATUS_IN_PROGRESS=진행 중
JIRA_STATUS_CODE_REVIEW=코드 리뷰
JIRA_STATUS_VERIFYING=검증 중
JIRA_STATUS_DONE=완료
JIRA_DRY_RUN=false
```

`JIRA_DRY_RUN=true`로 설정하면 실제 Jira 상태 변경 없이 workflow 동작을 확인합니다.

## CodeRabbit 설정 기준

CodeRabbit 리뷰는 `.coderabbit.yaml` 설정을 기준으로 한국어 자동 리뷰를 수행합니다.

이번 설정에서는 `AGENTS.md`, `CLAUDE.md` 기반 code guideline 활용을 사용하지 않습니다.

AI 에이전트가 작성한 PR도 PR 템플릿, Jira 번호 규칙, branch 규칙, required status check 기준을 동일하게 따라야 합니다.

## 민감정보 확인

PR에는 개인 환경 파일, 인증 정보, 토큰, 비밀번호를 포함하지 않습니다.

특히 다음 파일과 값이 포함되지 않았는지 확인합니다.

```plaintext
.env
.env.*
JIRA_API_TOKEN
JIRA_EMAIL
MONEW_DB_PASSWORD
```

공유 가능한 예시 값은 `.env.example`에만 작성합니다.
