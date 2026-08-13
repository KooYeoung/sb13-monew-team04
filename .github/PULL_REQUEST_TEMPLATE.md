## 관련 Jira

- Jira: MID4-번호
- 대표 Jira 티켓 1개만 작성합니다.

## 작업 내용

-

## 확인 방법

-

## 확인 사항

- [ ] branch 이름이 `<type>/MID4-번호-간단한-설명` 형식을 따른다.
- [ ] PR 제목이 `MID4-번호 작업 요약` 형식을 따른다.
- [ ] PR 본문의 `Jira:` 필드에 대표 Jira 번호를 작성했다.
- [ ] 로컬 실행 또는 테스트를 확인했다.
- [ ] 관련 도메인 담당자 리뷰 대상임을 확인했다.
- [ ] `develop` 대상 PR은 `develop-build-test` check 대상이며 build/test 통과가 필요함을 확인했다.
- [ ] `main` 대상 PR은 `develop -> main` 흐름이며 `main-source-guard` check 대상임을 확인했다.
- [ ] 충돌이 있으면 최신 `develop`을 작업 브랜치에 반영해 해결하고 테스트를 다시 실행했다.
- [ ] Squash merge로 병합하고, 병합 후 작업 브랜치를 삭제하는 기준을 확인했다.
- [ ] 불필요한 파일과 민감한 값이 포함되지 않았다.
