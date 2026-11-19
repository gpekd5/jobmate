# JobMate AI 개발 행동 규칙

Codex·Claude 등 모든 개발 에이전트의 공통 지침은 이 파일 하나로 관리한다. 서비스의 추천·자소서용 LLM 프롬프트와는 별개다.

- Issue, 관련 코드, **기능·API 명세서**와 필요한 컨벤션을 먼저 확인한다. 명세된 API·정책을 임의로 재설계하지 않고, 계약을 변경하면 관련 문서도 함께 갱신한다.
- 명확한 일반 구현·테스트·범위 안 버그 수정과 기존 API·DB·인증 설계 구현은 승인 없이 자율 진행한다. 사소한 작업에 긴 계획이나 단계별 보고를 강제하지 않는다. 영향이 크거나 복잡할 때만 짧게 계획과 근거를 제시한다.
- 새로운 중요 정책·아키텍처·DB 계약·인증·권한 결정이 불명확하거나 데이터 손실·비밀정보 유출·유료 인프라 비용 위험이 있으면 사용자 확인을 받는다.
- 변경 전 브랜치와 작업 트리를 확인하고 기존 사용자 변경을 보존한다. Issue 범위를 임의 확장하지 않는다.
- 관련 테스트·빌드·정적 검증만 수행하고 실제 명령·결과와 미실행 이유를 보고한다. 미실행을 PASS로 쓰지 않는다. 완료 전 변경 파일·문서 링크·diff를 확인한다.
- 비밀정보와 실제 이력서·지원정보·개인정보를 코드·로그·Commit·PR에 포함하지 않는다.
- PR 작성까지 진행할 수 있으며 최종 Merge는 사용자가 한다.
- `status:*` Label 자동 관리, Human 이해도 질문, 다중 AI 승인, 의무적인 독립 AI 리뷰, 모든 작업의 Evidence 문서를 도입하지 않는다. 별도 AI 가이드나 `CLAUDE.md`로 규칙을 중복하지 않는다.

상세 기준은 관련 문서만 선택해 읽는다.

- [JobMate 기능 명세](docs/020-product/feature-spec.md)
- [JobMate API 명세](docs/030-api/api-spec.md)
- [프로젝트 구조](docs/040-architecture/project-structure.md)
- [Java / Spring 컨벤션](docs/050-engineering/code-convention.md)
- [React / TypeScript 컨벤션](docs/050-engineering/frontend-convention.md)
- [테스트 컨벤션](docs/050-engineering/test-convention.md)
- [GitHub 규칙](docs/050-engineering/github-rules.md)
