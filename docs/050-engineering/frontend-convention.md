# React / TypeScript 컨벤션

[Issue #1](https://github.com/gpekd5/jobmate/issues/1)의 F 확정 기준이다. React / TypeScript / Vite를 사용하며 디렉터리 역할은 [프로젝트 구조](../040-architecture/project-structure.md)를 따른다.

- React 함수형 컴포넌트와 TypeScript `strict`를 사용한다. `any`를 지양하고 필요한 경우 이유를 설명한다.
- 컴포넌트·페이지는 `PascalCase`, 함수·변수·hook은 `camelCase`를 사용한다. hook은 `use*`로 시작하고 컴포넌트 파일명은 컴포넌트명과 일치시킨다.
- `pages/`는 화면 조합, `features/`는 기능 UI·상태·API 연계, `shared/`는 재사용 UI·HTTP 클라이언트·유틸을 담당한다.
- API 요청을 UI 컴포넌트에 산재시키지 않고 기능별 API 모듈로 분리한다. 요청·응답 타입을 명시한다.
- 로딩·오류·빈 결과 상태를 고려한다.
- 불필요한 전역 상태 관리와 추상화 라이브러리를 도입하지 않는다.
- ESLint/Prettier를 사용한다. 세부 룰셋은 환경 구축 때 정하며 필요 이상으로 엄격하게 적용하지 않는다. 이번 Issue에서는 도구 설정이나 설치를 하지 않는다.
