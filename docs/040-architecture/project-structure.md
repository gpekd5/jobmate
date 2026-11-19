# 프로젝트 구조

이 문서는 JobMate 저장소의 **목표 디렉터리 구조와 계층 책임에 대한 기준 문서**다. 아래는 목표 구조이며 해당 책임을 실제 구현할 때만 디렉터리를 만든다. 모든 계층과 폴더를 미리 생성할 필요는 없다.

## 모노레포

```text
jobmate/
├── backend/       # Java 17 / Spring Boot / Gradle / MySQL / Spring Data JPA
├── frontend/      # React / TypeScript / Vite
├── docs/          # 구조와 개발 기준
├── .github/       # Issue / PR 템플릿
├── AGENTS.md      # 공통 AI 개발 행동 규칙
└── README.md      # 서비스 소개와 주요 기능
```

로컬에서 먼저 실행 가능한 MVP를 만든다. 배포 방식은 후속 Issue에서 결정하며, 운영 자산이 필요해질 때 `ops/`를 추가한다.

## Backend: 도메인 우선

Java 소스 루트 아래에서 `job` 같은 도메인을 먼저 나누고, 각 도메인 내부에 필요한 역할만 둔다. 아래 경로는 역할 예시이며 필수 생성 목록이 아니다.

| 도메인 내부 경로 | 책임 |
| --- | --- |
| `presentation/controller`, `request`, `response` | HTTP 입력 검증·매핑, Service 호출, 응답 변환 |
| `application/service`, `command`, `result` | 유스케이스 조합, 입력·출력 계약, 트랜잭션 경계 |
| `application/port` | 외부 기술과의 경계가 실제 필요한 경우의 계약 |
| `domain/entity`, `policy`, `exception` | 도메인 상태·비즈니스 불변식·오류 |
| `infrastructure/repository` | Spring Data JPA 기반 영속화 |
| `infrastructure/repository/query` | 실제 필요한 복잡 조회 |
| `infrastructure/`의 기술별 하위 경로 | 외부 API 연동, 기술 구현, 주기 실행 |

Controller는 Repository를 직접 호출하지 않는다. 비즈니스 규칙은 Entity/Policy에, 유스케이스와 트랜잭션은 Application Service에 둔다. 외부 기술 구현은 Infrastructure가 소유한다. 단순 JPA Repository를 형식적인 Port/Adapter로 감싸지 않는다.

채용공고 API 수집은 `job` 도메인의 Infrastructure에 둔다. 다중 소스 전환 등 실제 경계가 필요할 때만 Port/Adapter를 도입한다. 빈 계층·인터페이스를 미리 만들지 않으며, `common`에는 여러 기능이 실제로 공유하는 기반만 둔다.

세부 네이밍과 작성 방식은 [Java / Spring 컨벤션](../050-engineering/code-convention.md)을 따른다.

## Frontend: 가벼운 기능 중심 구조

| `frontend/src/` 내부 경로 | 책임 |
| --- | --- |
| `pages/` | 화면과 기능의 조합 |
| `features/` | 기능별 UI·상태·API 연계 |
| `shared/` | 재사용 UI·HTTP 클라이언트·유틸 |

기능별 API 요청·응답 타입과 호출 모듈은 해당 기능이 소유한다. 한 기능에서만 쓰는 코드를 미리 shared로 올리지 않는다. 세부 기준은 [React / TypeScript 컨벤션](../050-engineering/frontend-convention.md)을 따른다.

## 참고

[BobFull 프로젝트 구조](https://github.com/gpekd5/bobfull-backend/blob/develop/docs/040-architecture/project-structure.md)의 도메인 우선·책임별 분리를 참고했다. BobFull의 운영 구성과 관리 절차를 복제하지 않는다. JobMate의 패키지 책임은 이 문서를, Java 작성 방식은 JobMate 코드 컨벤션을 우선한다.
