# Git 커밋 컨벤션 설정

## 커밋 메시지 형식

MID4 프로젝트의 커밋 메시지 첫 줄은 다음 형식을 사용합니다.

```plaintext
<type>: MID4-번호 변경 내용
```

예시는 다음과 같습니다.

```plaintext
feat: MID4-12 회원가입 기능 구현
fix: MID4-20 로그인 예외 처리 수정
docs: MID4-5 커밋 컨벤션 문서 정리
```

사용할 수 있는 type은 다음과 같습니다.

| type | 의미 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 리팩토링 |
| `test` | 테스트 추가 또는 수정 |
| `docs` | 문서 수정 |
| `chore` | 빌드, 설정, 기타 작업 |

50자 제한은 현재 정책에서 적용하지 않습니다.

## 팀원용 Git 설정 스크립트

저장소를 받은 뒤 각 사용자 로컬에서 본인 clone에 맞는 스크립트를 한 번 실행합니다.

Mac, Linux, Git Bash 환경에서는 다음 명령을 사용합니다.

```bash
sh scripts/setup-git.sh
```

Windows CMD 또는 PowerShell 환경에서는 다음 명령을 사용합니다.

```powershell
.\scripts\setup-git.bat
```

스크립트는 Git 저장소 내부에서만 동작하며, 저장소 local Git 설정에 아래 값을 적용한 뒤 결과를 출력합니다.

```plaintext
commit.template=.gitmessage.txt
core.hooksPath=.githooks
```

다른 팀원이 작업 중인 저장소에서는 대신 실행하지 않습니다. 각 팀원은 본인 PC의 본인 clone에서 직접 실행합니다.

## 수동 Git 설정

저장소를 받은 뒤 각 사용자 로컬에서 아래 명령을 실행합니다.

```powershell
git config --local core.hooksPath .githooks
git config --local commit.template .gitmessage.txt
```

`core.hooksPath`를 설정하면 `.githooks/commit-msg`가 커밋 메시지를 검사합니다.

`commit.template`을 설정하면 커밋 메시지 작성 화면에 `.gitmessage.txt`의 예시가 표시됩니다.

## commit-msg hook 검증 기준

hook은 커밋 메시지 파일에서 주석과 빈 줄을 제외한 첫 번째 실제 메시지 줄을 검사합니다.

다음 형식은 통과합니다.

```plaintext
feat: MID4-56 커밋 훅 추가
```

다음 형식은 실패합니다.

```plaintext
MID4-56 커밋 훅 추가
feat: 커밋 훅 추가
feat: MID4-56
feat: MID4-56 <변경 내용 없이 공백만 입력>
```

Git hook 검증은 저장소 전달 후 각 사용자 로컬 설정 단계에서 수행합니다.
