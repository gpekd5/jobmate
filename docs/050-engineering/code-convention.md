# Java / Spring 코드 컨벤션

[Issue #1](https://github.com/gpekd5/jobmate/issues/1)의 A~D 확정 기준이다. [프로젝트 구조](../040-architecture/project-structure.md)와 함께 적용한다. [BobFull 코드 컨벤션](https://github.com/gpekd5/bobfull-backend/blob/develop/docs/050-engineering/code-convention.md)을 참고하되 JobMate 기준을 우선한다.

## A. 네이밍과 형식

- 일반 Java 스타일은 [NAVER CAMPUS HACKDAY Java 코딩 컨벤션](https://naver.github.io/hackday-conventions-java/)을 참고한다. 충돌 시 Issue의 확정 기준과 이 문서를 우선한다.
- 타입·파일은 `PascalCase`, 메서드·변수는 `camelCase`, 상수·enum 상수는 `UPPER_SNAKE_CASE`. 파일명과 public 타입명을 일치시킨다.
- 들여쓰기는 공백 4칸. 사용하지 않는 import와 wildcard import를 지양하고 파일 끝 newline을 유지한다.
- 주석·JavaDoc을 남발하지 않는다. 이름에서 드러나지 않는 이유(Why), 비즈니스 제약, 락·트랜잭션 이유를 설명한다.

## B. 계층·역할·접미사

| 계층 | 경로 | 이름과 책임 |
| --- | --- | --- |
| Presentation | `presentation/controller` | `*Controller`: HTTP 처리 |
| Presentation | `presentation/request`, `presentation/response` | `*Request`, `*Response`: HTTP 입출력 |
| Application | `application/service` | `*Service`: 유스케이스. 필요할 때만 `*QueryService`, `*CommandService` |
| Application | `application/command`, `application/result` | `*Command`, `*Result`: 유스케이스 입출력 |
| Application | `application/port` | `*Port`: 실제 필요한 외부 경계 |
| Domain | `domain/entity`, `domain/policy`, `domain/exception` | 실제 도메인명, `*Policy`, `*ErrorCode` |
| Infrastructure | `infrastructure/repository` | `*Repository`: 영속화 |
| Infrastructure | 외부 연동·기술별 경로 | `*Adapter`, `*Scheduler`: 실제 연동 구현·주기 실행 역할에만 사용 |

- 도메인을 먼저 나누고 실제 필요한 계층·패키지만 만든다. `common`은 진짜 공유 기반만 둔다.
- `DTO`, `Dto`, `Entity`, `VO`를 관성적으로 덧붙이지 않는다. Entity는 실제 도메인명을 사용한다.
- 복잡 조회가 실제로 필요할 때만 `infrastructure/repository/query` 아래 별도 검색 Repository를 둔다. 단순 JPA Repository까지 래핑하지 않는다.
- API 수집은 `job` 도메인의 Infrastructure에 둔다. 다중 소스 전환 같은 경계가 필요할 때만 Port/Adapter를 만든다.

## C. DTO·의존성·Entity

- `Request`, `Response`, `Command`, `Result`는 `record` 우선. `Dto` 접미사와 record의 Lombok `@Getter`, `@Setter`, `@Builder` 기본 사용을 금지한다.
- Controller는 입력 검증·HTTP 매핑·Service 호출·응답 변환만 담당한다. Repository 호출과 비즈니스 규칙 구현을 금지하고, Entity를 HTTP Response로 직접 반환하지 않는다.
- 사용자 ID 등 인증으로 확정 가능한 값은 요청 본문을 신뢰하지 않고 인증 컨텍스트에서 가져온다.
- Spring bean은 `final` 필드 기반 생성자 주입을 사용한다. `@RequiredArgsConstructor`를 사용할 수 있으며 `@Autowired` 필드 주입을 지양한다.
- Entity는 기본 `@Getter`와 보호된 기본 생성자를 사용한다. Lombok 사용 시 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 표현할 수 있다.
- 공개 Setter를 지양하고 `close()`, `markExpired()`처럼 의미 있는 메서드로 상태를 변경한다.
- `@Data`, 무분별한 `@Builder`, `@AllArgsConstructor`, 전역 Setter를 지양한다. 필요한 경우 이유가 있는 예외를 허용한다.

## D. 검증·트랜잭션·오류·로그

- HTTP 입력 형식은 Bean Validation (`@Valid`, `@NotBlank`, `@Positive` 등)으로, 비즈니스 불변식은 Domain/Policy에서 검증한다. 같은 검증을 매 계층에 복제하지 않는다.
- 트랜잭션 경계는 Application Service가 관리한다. 조회는 `@Transactional(readOnly = true)`, 쓰기는 `@Transactional`을 사용한다. propagation 변경은 필요 근거가 있을 때만 한다.
- 외부 채용 API·LLM의 느린 네트워크 호출을 DB 트랜잭션에 무분별하게 묶지 않는다.
- 공통 `ApiResponse<T>`, `GlobalExceptionHandler`, `CustomException`과 도메인별 `*ErrorCode`를 원칙으로 한다. 실제 응답 포맷과 오류 필드는 API 구현 Issue에서 최초 확정한다. 이번 문서에서 구체 필드를 선결정하지 않는다.
- 기술 오류와 사용자에게 보여줄 비즈니스 오류를 구분한다.
- SLF4J를 사용하고 로그가 필요한 클래스에만 `@Slf4j`를 둔다. 문자열 연결 대신 `log.info("event=JOB_COLLECTED count={}", count)`처럼 파라미터화한다.
- 비밀번호·토큰·이력서·개인정보 원문을 로그에 남기지 않는다.
- 반복되는 단순 매핑과 작은 유틸을 과도하게 추상화하지 않는다.
