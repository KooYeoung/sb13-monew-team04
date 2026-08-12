# GitHub Branch Protection 설정

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
feature/MID4-58-test -> main
fork-repository:develop -> main
```

`feature/*` 작업 브랜치는 `develop`으로 PR을 생성합니다.

`main` 반영이 필요하면 먼저 `develop`에 병합한 뒤, `develop -> main` PR을 생성합니다.

## required status check 설정

GitHub repository settings에서 `main` branch protection 또는 ruleset을 설정할 때 required status check에 다음 check를 추가합니다.

```plaintext
main-source-guard
```

이 check를 required로 지정해야 `feature/* -> main` 또는 fork source PR이 실수로 merge되는 것을 막을 수 있습니다.

## 적용 기준

* `main`에는 직접 push하지 않습니다.
* `main` 대상 PR은 `develop -> main` 흐름만 사용합니다.
* `develop` 대상 PR은 기능 작업 브랜치에서 생성합니다.
* branch protection 또는 ruleset 실제 적용은 GitHub UI에서 수행합니다.
