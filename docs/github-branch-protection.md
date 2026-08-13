# GitHub Branch Protection 설정

## 공통 주의

GitHub Actions workflow 파일만 추가하면 merge 차단이 자동으로 보장되지 않습니다.

각 branch protection 또는 ruleset에서 필요한 workflow check를 required status check로 지정해야 실제 merge 차단 기준으로 동작합니다.

Repository merge 설정은 Squash merge만 사용하는 것을 기준으로 합니다.

GitHub repository settings에서 다음 기준을 적용합니다.

```plaintext
allow_squash_merge=true
allow_merge_commit=false
allow_rebase_merge=false
delete_branch_on_merge=true
```

이 설정은 repository 관리자 권한이 있는 사용자가 적용합니다.

## main PR source guard

`main` 브랜치는 운영 반영 흐름으로 사용하므로, `main` 대상 PR은 같은 저장소의 `develop` 브랜치에서만 생성합니다.

이 기준은 `.github/workflows/main-pr-source-guard.yml`의 `main-source-guard` check로 확인합니다.

## 허용되는 PR

다음 PR만 `main-source-guard`를 통과합니다.

```plaintext
develop -> main
```

단, source branch는 같은 GitHub 저장소의 `develop`이어야 합니다.

## 실패해야 하는 PR

다음 PR은 `main-source-guard`에서 실패하는 것이 정상입니다.

```plaintext
feat/MID4-58-test -> main
fork-repository:develop -> main
```

`<type>/MID4-번호-간단한-설명` 형식의 작업 브랜치는 `develop`으로 PR을 생성합니다.

`main` 반영이 필요하면 먼저 `develop`에 병합한 뒤, `develop -> main` PR을 생성합니다.

## required status check 설정

GitHub repository settings에서 `main` branch protection 또는 ruleset을 설정할 때 required status check에 다음 check를 추가합니다.

```plaintext
main-source-guard
```

이 check를 required로 지정해야 작업 브랜치에서 `main`으로 향하는 PR 또는 fork source PR이 실수로 merge되는 것을 막을 수 있습니다.

## develop PR build/test

`develop` 브랜치는 기능 작업을 통합하는 개발 브랜치입니다.

`develop` 대상 PR에서는 `.github/workflows/develop-build-test.yml`의 `develop-build-test` check가 실행됩니다.

## 허용되는 develop PR

다음 PR은 `develop-build-test` 대상입니다.

```plaintext
feat/MID4-59-test -> develop
```

이 check는 Java 17 환경에서 Gradle wrapper로 build와 test를 실행합니다.

```plaintext
./gradlew clean build
```

build 또는 test가 실패하면 `develop-build-test` check도 실패합니다.

## 실패해야 하는 develop PR

다음 PR은 source branch guard에서 실패하는 것이 정상입니다.

```plaintext
main -> develop
```

`main` 브랜치는 운영 반영 기준이므로 `develop`으로 되돌리는 source branch로 사용하지 않습니다.

## develop required status check 설정

GitHub repository settings에서 `develop` branch protection 또는 ruleset을 설정할 때 required status check에 다음 check를 추가합니다.

```plaintext
develop-build-test
```

이 check를 required로 지정해야 build 또는 test가 실패한 PR이 `develop`에 merge되는 것을 막을 수 있습니다.

## 적용 기준

* `main`에는 직접 push하지 않습니다.
* `main` 대상 PR은 `develop -> main` 흐름만 사용합니다.
* `develop` 대상 PR은 `<type>/MID4-번호-간단한-설명` 형식의 작업 브랜치에서 생성합니다.
* branch protection 또는 ruleset 실제 적용은 GitHub UI에서 수행합니다.
