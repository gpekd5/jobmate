# GitHub 규칙

## 브랜치와 작업 흐름

브랜치는 `main`과 `feature/<issue-number>-<summary>` 두 종류만 사용한다. 새 작업은 최신 `main`에서 시작하며 PR base는 항상 `main`이다.

```text
Issue → feature 브랜치 → 작업·검증 → main 대상 PR → 사용자 Merge
```

Issue #1의 브랜치는 `feature/1-project-setup`이다. 변경 전 현재 브랜치와 작업 트리를 확인하고, 기존 사용자 변경은 보존한다. `main`에 직접 작업하거나 직접 push하지 않는다.

## Issue·Commit·PR

- [Issue 템플릿](../../.github/ISSUE_TEMPLATE/task.md)에 목적, 포함·제외 범위, 완료 조건을 간결히 작성한다.
- Commit은 관련 변경 단위로 묶고 무엇을 바꿨는지 설명한다. 예: `docs: 프로젝트 구조와 개발 컨벤션 정립 (#1)`.
- [PR 템플릿](../../.github/pull_request_template.md)을 사용해 관련 Issue, 변경 내용, 포함·제외 범위, 완료 조건, 실제 검증 결과와 미실행 이유를 기록한다.
- PR에서 Issue를 연결한다. 완료한 Issue는 `Closes #1`처럼 연결할 수 있다.
- PR 생성까지 작업하고 최종 Merge는 사용자가 수행한다.

AI의 구체적인 자율 진행·사용자 확인 기준은 [AGENTS.md](../../AGENTS.md)에서만 관리한다.
