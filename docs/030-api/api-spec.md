# JobMate API 명세서 (정책 합의 v1.2)

> 기준일: 2026-11-20 · 문서 작업: #19 · [기능 명세서](../020-product/feature-spec.md)
>
> **이 문서는 구현 전 API 설계안이다.** 아래 경로는 현재 동작한다고 보장하지 않으며, 기능별 Issue에서 구현/테스트한 시점에 계약을 확정한다. 외부 채용 사이트 API 경로를 뜻하지 않는다.
> 개인 데이터 API는 #11 인증 전에 **신뢰할 수 있는 루프백 로컬 개발환경에서만** 사용할 수 있다. 실제 업로드 파일과 사용자 정보는 Git에 넣지 않는다.

## 1. 공통 계약

- Base path: `/api/v1`; JSON `camelCase`, `Content-Type: application/json` (파일 업로드만 `multipart/form-data`).
- ID: 서버에서 발급한 Long 식별자를 JSON number로 반환. 숫자는 예시이며 외부 소스 공고 ID와 구분한다.
- Date/time: ISO 8601 offset 포함 (예: `2026-11-20T09:00:00+09:00`). 알 수 없는 날짜는 `null`.
- Enum은 대문자 `UPPER_SNAKE_CASE`로 반환; 아래 Enum 후보는 구현 전 조정 가능.
- 모든 데이터 반환은 `ApiResponse<T>` 사용. `PATCH`는 일부 변경, `PUT`은 명시한 리소스 전체 교체. 성공 리소스 생성은 HTTP 201; 비동기 작업 접수는 HTTP 202; 삭제는 HTTP 204 (본문 없음).
- 리스트는 기본 `page=0, size=20`; 최대 `size=100` [설계안]. 응답 순서가 불안정하지 않도록 동일 우선순위 내 ID 등 보조 정렬 사용.
- **인증 표기:** `PUBLIC-LOCAL` = 로컬 테스트 전용/추후 공개 여부 별도 검토, `USER` = 로그인한 본인만, `INTERNAL` = 스케줄러/운영자 전용. 공개 서비스에서 `USER/INTERNAL` API는 반드시 인증·인가.
- 오류의 `code`는 안정적 애플리케이션 오류 식별자. 실패를 200 OK로 감추지 않음.
- API 출력에 이력서 원문, 외부 사이트 접근 키, LLM 프롬프트/민감 문서 내용, DB 내부 정보 노출 금지.

### 1.1 공통 성공/오류 응답 [설계안]

```json
{
  "success": true,
  "data": {
    "jobId": 101,
    "title": "백엔드 개발자"
  }
}
```

```json
{
  "success": false,
  "error": {
    "code": "JOB_NOT_FOUND",
    "message": "공고를 찾을 수 없습니다.",
    "details": []
  }
}
```

- 200 OK: 조회/수정 성공 · 201 Created: 생성 · 202 Accepted: 비동기 작업 접수 · 204 No Content: 삭제.
- 400 Bad Request: 잘못된 입력/쿼리, validation · 401 Unauthorized: 로그인 필요 · 403 Forbidden: 권한 없음 또는 AI 전송 동의 미비 · 404 Not Found: 리소스 부재 · 409 Conflict: 중복/상태 충돌 · 413 Payload Too Large · 415 Unsupported Media Type · 422 Unprocessable Entity: 입력은 유효하나 분석 근거 불충분 · 429 Too Many Requests · 500 Internal Server Error / 502·503 외부/내부 서비스 불가.
- validation details 설계: `[{ "field":"title", "reason":"필수 입력입니다." }]`. 원문 값·비밀번호 포함 금지.
- 마감된 공고도 조회 가능하며 원문 이동 및 지원 상태 판단은 사용자가 한다. 공고 상태 자체와 수집 실패를 혼동하지 않는다.

## 2. Endpoint 전체 목록

| 구분 | Method | Path | 설명 | 접근 | Issue |
|---|---|---|---|---|---|
| 상태 | GET | `/actuator/health` | DB 포함 서버 상태 (기존) | LOCAL/운영 제한 | #4 |
| 공고 | GET | `/api/v1/jobs` | 전체 공고 검색·필터·페이징 | PUBLIC-LOCAL | #5 |
| 공고 | GET | `/api/v1/jobs/{jobId}` | 공고 상세·담당업무·자격·우대 | PUBLIC-LOCAL | #5 |
| 공고 개발용 | POST | `/api/v1/dev/jobs` | 개발용 테스트 공고 등록 | LOCAL ONLY | #5 |
| 공고 개발용 | PATCH | `/api/v1/dev/jobs/{jobId}` | 개발용 공고 수정 | LOCAL ONLY | #5 |
| 문서 | POST | `/api/v1/me/documents` | 이력서/포트폴리오 파일 업로드 | USER | #8 |
| 문서 | GET | `/api/v1/me/documents` | 파일 메타데이터 조회 | USER | #8 |
| 문서 | DELETE | `/api/v1/me/documents/{documentId}` | 내 파일 삭제 | USER | #8 |
| 포트폴리오 링크 | POST | `/api/v1/me/portfolio-links` | 포트폴리오 URL 등록 | USER | #8 |
| 포트폴리오 링크 | GET | `/api/v1/me/portfolio-links` | 링크 목록 | USER | #8 |
| 포트폴리오 링크 | DELETE | `/api/v1/me/portfolio-links/{linkId}` | 링크 삭제 | USER | #8 |
| 프로필 | POST | `/api/v1/me/profile/analyses` | 문서 기반 프로필 추출 시작 | USER | #8 |
| 프로필 | GET | `/api/v1/me/profile/analyses/{analysisId}` | 추출 진행/결과 확인 | USER | #8 |
| 프로필 | GET | `/api/v1/me/profile` | 확정한 프로필 조회 | USER | #8 |
| 프로필 | PUT | `/api/v1/me/profile` | 추출 결과 검토 후 수정/확정 | USER | #8 |
| 희망조건 | GET | `/api/v1/me/preferences` | 선택 선호조건 조회 | USER | #8 |
| 희망조건 | PUT | `/api/v1/me/preferences` | 희망 직무/지역 등 설정 | USER | #8 |
| AI 동의 | GET | `/api/v1/me/ai-consent` | 외부 AI 전송 동의 확인 | USER | #8, #11 |
| AI 동의 | PUT | `/api/v1/me/ai-consent` | 동의 범위 변경/철회 | USER | #8, #11 |
| 추천 | GET | `/api/v1/recommendations` | 적합도 순 추천 피드 | USER | #9, #6 |
| 추천 | GET | `/api/v1/jobs/{jobId}/match` | 내 프로필 기준 적합도 상세 | USER | #9 |
| 추천 | POST | `/api/v1/recommendations/refresh` | 필요 시 재평가 요청 (중복/비용 제한) | USER | #9 |
| 개별 AI 분석 | POST | `/api/v1/jobs/{jobId}/match/analyses` | 미분석 공고 직접 분석 요청 | USER | #9 |
| 확인/미확인 | PUT | `/api/v1/me/jobs/{jobId}/viewed` | 상세 진입 후 확인(멱등) | USER | #6 |
| 확인/미확인 | DELETE | `/api/v1/me/jobs/{jobId}/viewed` | 미확인 되돌리기 | USER | #6 |
| 관심 공고 | GET | `/api/v1/me/bookmarks` | 관심 공고 목록 | USER | #10 |
| 관심 공고 | PUT | `/api/v1/me/bookmarks/{jobId}` | 관심 공고 저장(멱등) | USER | #10 |
| 관심 공고 | DELETE | `/api/v1/me/bookmarks/{jobId}` | 관심 공고 해제 | USER | #10 |
| 지원 | GET | `/api/v1/me/applications` | 지원 이력 목록 | USER | #10 |
| 지원 | POST | `/api/v1/me/applications` | 지원 기록 추가 | USER | #10 |
| 지원 | GET | `/api/v1/me/applications/{applicationId}` | 지원 이력 상세 | USER | #10 |
| 지원 | PATCH | `/api/v1/me/applications/{applicationId}` | 지원 상태·메모 수정 | USER | #10 |
| 지원 | DELETE | `/api/v1/me/applications/{applicationId}` | 지원 기록 삭제 | USER | #10 |
| 로그인 | GET | `/oauth2/authorization/kakao` | Spring Security 카카오 로그인 시작 | browser | #11 |
| 로그인 | GET | `/login/oauth2/code/kakao` | Spring Security OAuth 콜백 | provider | #11 |
| 로그인 | GET | `/api/v1/auth/me` | 로그인 사용자 정보 | USER | #11 |
| 로그인 | POST | `/api/v1/auth/logout` | 로그아웃 (구체 방식 후속 결정) | USER | #11 |
| 개인정보 | DELETE | `/api/v1/me/account` | 탈퇴 및 데이터 삭제 요청 | USER | #11 |
| 수집 운영 | POST | `/api/v1/internal/collection-runs` | 허용 소스 수집 수동 실행 | INTERNAL | #7 |
| 수집 운영 | GET | `/api/v1/internal/collection-runs` | 수집 실행 이력/상태 | INTERNAL | #7 |
| 기업 분석 | POST | `/api/v1/jobs/{jobId}/company-analyses` | 기업 분석 시작 | USER | #14 |
| 기업 분석 | GET | `/api/v1/jobs/{jobId}/company-analyses/{analysisId}` | 기업 분석 결과 | USER | #14 |
| 자소서 | POST | `/api/v1/me/cover-letters` | 선택 공고 기준 초안 생성 | USER | #15 |
| 자소서 | GET | `/api/v1/me/cover-letters` | 초안 목록 | USER | #15 |
| 자소서 | GET | `/api/v1/me/cover-letters/{id}` | 초안 조회 | USER | #15 |
| 자소서 | PATCH | `/api/v1/me/cover-letters/{id}` | 초안 수정 | USER | #15 |
| 자소서 | DELETE | `/api/v1/me/cover-letters/{id}` | 초안 삭제 | USER | #15 |

**중요:** `/api/v1/internal/*` 및 `/api/v1/dev/*`는 일반 사용자가 접근할 수 있는 공개 API가 아니다. 실제 인증 이전에는 루프백 바인딩 및 로컬 프로파일로만 구현하며 운영에서는 비활성화/접근 차단한다. 자동 수집 자체는 스케줄러가 구동하므로 **관리용 POST Endpoint는 필수 기능이 아니다.**

## 3. 채용공고 계약 (#5·#7)

### 3.1 목록

`GET /api/v1/jobs?keyword=Spring&region=서울&jobRole=BACKEND&source=SARAMIN&activeOnly=true&page=0&size=20&sort=NEWEST`

- 모두 선택 쿼리: `keyword`, `region`, `jobRole`, `source`, `activeOnly` (기본 true), `page`, `size`, `sort=NEWEST|DEADLINE`. 인증 사용자는 `viewStatus=ALL|UNREAD|READ` 필터도 사용 가능; 비인증 호출에는 개인 읽음 정보를 노출하지 않는다.
- 복잡한 키워드 자연어 검색/벡터 검색은 MVP 요구사항이 아니다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "jobId": 101,
        "source": "SARAMIN",
        "companyName": "예시 기업",
        "title": "Java 백엔드 개발자",
        "region": "서울",
        "employmentType": "FULL_TIME",
        "experienceText": "신입/경력",
        "postedAt": "2026-11-18T09:00:00+09:00",
        "deadlineAt": null,
        "active": true,
        "originalUrl": "https://example.com/jobs/101",
        "detailAvailability": "UNAVAILABLE",
        "lastCollectedAt": "2026-11-20T08:00:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

> `example.com`과 공고 내용은 **Mock 예시**다. 사람인/원티드 실제 응답이나 사용 권한을 주장하지 않는다.

### 3.2 상세

`GET /api/v1/jobs/101`

```json
{
  "success": true,
  "data": {
    "jobId": 101,
    "source": "MANUAL",
    "externalJobId": null,
    "companyName": "예시 기업",
    "title": "Java 백엔드 개발자",
    "jobRole": "BACKEND",
    "region": "서울",
    "employmentType": "FULL_TIME",
    "experienceText": "신입/경력",
    "educationText": null,
    "salaryText": null,
    "postedAt": "2026-11-18T09:00:00+09:00",
    "deadlineAt": null,
    "active": true,
    "originalUrl": "https://example.com/jobs/101",
    "responsibilities": ["Spring 기반 API 개발", "서비스 운영 개선"],
    "requiredQualifications": ["Java/Spring 프로젝트 경험"],
    "preferredQualifications": ["MySQL 성능 튜닝 경험"],
    "skills": ["Java", "Spring Boot", "MySQL"],
    "workConditions": [],
    "detailAvailability": "AVAILABLE",
    "detailSource": "USER_PROVIDED",
    "detailExtractedAt": "2026-11-20T09:00:00+09:00",
    "lastCollectedAt": "2026-11-20T08:00:00+09:00"
  }
}
```

- `detailAvailability`: `AVAILABLE | PARTIAL | UNAVAILABLE | RESTRICTED` [설계안].
- `detailSource`: `OFFICIAL_API | PERMITTED_CONTENT | USER_PROVIDED | UNKNOWN` [설계안].
- 부분/미제공 필드는 `null` 또는 빈 배열을 일관되게 사용. **미확인과 '해당 없음'은 구분**하도록 실 구현에서는 필드별 `availability`가 필요할 수 있다.
- 수집 가능 여부와 별도로 **저장/재가공/LLM 전송 권리**가 있는지 점검한다. 권한 없는 원문은 반환·영속화하지 않는다.
- `requiredQualifications`/ `preferredQualifications`는 설명 문자열 목록이며, LLM에 의한 구조화·근거 표현은 추천 상세에서 확인한다.

### 3.3 개발용 공고 등록 / 수정 (로컬 전용)

`POST /api/v1/dev/jobs` · `PATCH /api/v1/dev/jobs/{jobId}`

```json
{
  "companyName": "예시 기업",
  "title": "백엔드 개발자",
  "jobRole": "BACKEND",
  "region": "서울",
  "originalUrl": "https://example.com/jobs/101",
  "responsibilities": ["API 개발"],
  "requiredQualifications": ["Java 기본기"],
  "preferredQualifications": ["Spring Boot 프로젝트"],
  "skills": ["Java", "Spring Boot"]
}
```

- 서버에서 `source=MANUAL`을 설정. 외부 식별자가 없는 공고에는 내부 ID와 명시적 테스트 출처를 사용.
- 필수 입력: `companyName`, `title`; URL 및 상세 섹션은 입력 시 유효성 검증. 임의 사이트 원문 자동 다운로드를 의미하지 않음.
- 생성: 201 `{ "success":true, "data":{ "jobId":101 } }`; 수정: 200. 운영에서 이 Endpoint 비활성화.
- 제공처 API로 받은 공고의 자동 동기화/중복 정책은 `source + externalJobId` 고유성 및 트랜잭션에 기반. 같은 외부 ID의 변경은 갱신, 수집 실패를 마감으로 취급하지 않음.

## 4. 사용자 문서·포트폴리오·프로필 계약 (#8)

### 4.1 문서 업로드/목록/삭제

`POST /api/v1/me/documents`: `multipart/form-data`, 필드 `type=RESUME|PORTFOLIO`, `file=<binary>`

```json
{
  "success": true,
  "data": {
    "documentId": 14,
    "type": "RESUME",
    "fileName": "resume.pdf",
    "analysisStatus": "NOT_ANALYZED",
    "uploadedAt": "2026-11-20T09:10:00+09:00"
  }
}
```

- 이력서는 PDF/DOCX, 파일당 최대 10 MiB로 제한한다. 확장자/MIME/파일 시그니처 검사, 악성·매크로/암호화/손상된 파일 거절, 소유권 보호/비공개 경로·임의 저장 키를 사용한다.
- 사용자가 삭제/교체할 때까지 원본을 비공개 보관한다. 삭제/탈퇴 시 원본·추출물·관련 캐시의 접근을 즉시 차단하고 삭제하며, 백업이 있다면 최대 30일 이내 파기하고 복원 시 삭제 기록을 재적용한다.
- **업로드만 하면 NOT_ANALYZED, AI 호출 없음.** 사용자 'AI 분석하기' 명시 요청에서만 호출한다. 분석 성공/실패는 확정 프로필 상태와 별개다.
- `GET /api/v1/me/documents`: 메타데이터 목록(원본 파일/추출 본문은 기본 반환하지 않음). 문서 분석 상태는 `NOT_ANALYZED | ANALYZING | ANALYZED | FAILED`.
- `DELETE /api/v1/me/documents/{documentId}`: 204. 원본·추출 결과·캐시·백업의 삭제/파기 정책 적용, 다른 사용자 소유 ID는 404/403 정책에 맞게 차단.

### 4.2 포트폴리오 링크

`POST /api/v1/me/portfolio-links`

```json
{ "url": "https://github.com/example-user/example-project", "description": "Spring Boot 프로젝트" }
```

201 응답 `{ "success":true, "data":{ "linkId":3, "url":"https://github.com/example-user/example-project" } }`.

- `GET /api/v1/me/portfolio-links` / `DELETE /api/v1/me/portfolio-links/{linkId}`.
- URL 입력은 링크 등록일 뿐 **자동 GitHub 전체 계정 크롤링/비공개 저장소 접근 승인**이 아님. 서버가 링크를 가져오게 된다면 SSRF 방어, 호스트 허용 목록, 명시적 가져오기 범위와 권한 확인이 필요.

### 4.3 AI 분석 시작 및 상태 조회

`POST /api/v1/me/profile/analyses`

```json
{
  "documentIds": [14],
  "portfolioLinkIds": [3],
  "analysisMode": "EXTERNAL_LLM"
}
```

- 초기 분석은 **사용자 버튼 클릭으로만 실행하는 OpenAI API 기반 EXTERNAL_LLM**이다. 별도의 업체·분석 목적·전송 범위 동의를 서버에서 확인하고 연락처/이메일/상세 주소 등 개인정보를 마스킹·최소화한다. 단순 요청 플래그는 동의를 대신하지 않는다. LOCAL_ONLY 모델 모드는 초기 범위 제외.
- `LOCAL_ONLY`는 구현된 로컬 추출이 있는 경우만 지원, 미지원이면 명확한 오류를 반환. API 설계가 모든 분석 모드의 구현을 강제하지 않음.
- 분석은 지연될 수 있으므로 202 + 추적 ID를 반환한다.

```json
{
  "success": true,
  "data": { "analysisId": 9001, "status": "PENDING" }
}
```

`GET /api/v1/me/profile/analyses/9001`

```json
{
  "success": true,
  "data": {
    "analysisId": 9001,
    "status": "SUCCEEDED",
    "result": {
      "workExperiences": [
        { "role": "장비 SW 개발", "context": "실무", "evidence": "이력서 경력 섹션", "confidence": "HIGH" }
      ],
      "skills": [
        { "name": "Spring Boot", "context": "TEAM_PROJECT", "evidence": "포트폴리오 프로젝트", "confidence": "HIGH" }
      ],
      "projects": [],
      "education": [],
      "certifications": []
    },
    "createdAt": "2026-11-20T09:12:00+09:00",
    "completedAt": "2026-11-20T09:12:05+09:00"
  }
}
```

- 분석 작업 상태는 `PENDING | RUNNING | SUCCEEDED | FAILED`, 문서 상태는 `NOT_ANALYZED | ANALYZING | ANALYZED | FAILED`로 구분한다. `FAILED`일 때 `result:null`과 안전한 오류 코드. 명시 재분석 클릭 없이 자동 재호출하지 않는다.
- `result`는 **사용자 확인 전 초안**. 자동으로 확정 프로필을 덮어쓰지 않는다.
- 연차·기술 수준·학력 등을 이력서 밖에서 추측하지 않는다. 분석 오류·불완전한 PDF·예산 초과 시에도 원본 유지.

### 4.4 프로필 및 희망 조건

`GET /api/v1/me/profile` · `PUT /api/v1/me/profile`

```json
{
  "profileVersion": 2,
  "confirmed": true,
  "workExperiences": [
    { "role": "제조 장비 소프트웨어", "employmentType": "WORK", "summary": "장비 제어 및 연동" }
  ],
  "skills": [
    { "name": "Java", "context": "TRAINING_PROJECT" },
    { "name": "C#", "context": "WORK" }
  ],
  "projects": [],
  "education": [],
  "certifications": []
}
```

- `GET`의 반환 형태는 위 필드가 `data`로 감싸진 형태. `PUT`도 승인·수정된 데이터로 전체 교체하며 서버가 `profileVersion`을 증가시킨다.
- 프로필에 없는 항목을 임의 보충하지 않는다. 파일을 새로 올려도 기존 확정 프로필은 유지하고, 사용자가 분석 결과를 검토·확정한 뒤에만 `profileVersion`을 올린다.
- 충돌 검출을 위한 버전 기반 갱신은 고려하고, 구현 시 단일 사용자 흐름에 맞게 결정한다.

`GET /api/v1/me/preferences` · `PUT /api/v1/me/preferences`

```json
{
  "desiredRoles": ["BACKEND", "MES"],
  "regions": ["SEOUL", "GYEONGGI"],
  "employmentTypes": ["FULL_TIME"],
  "minSalary": null,
  "avoidDispatch": true,
  "avoidFrequentTravel": true
}
```

- **모든 선호 항목은 선택**. null/빈 배열은 필터 미적용. `avoid*` 항목의 강제 제외 vs 점수 감점 여부는 추천 정책에서 확정.
- 지역·직무 등 Enum 값은 DB/프론트와 일치시켜 단일 계약으로 관리한다.

### 4.5 외부 AI 전송 동의

`GET /api/v1/me/ai-consent` · `PUT /api/v1/me/ai-consent`

```json
{
  "provider": "OPENAI",
  "purposes": ["PROFILE_EXTRACTION", "JOB_MATCHING"],
  "consented": false
}
```

- 기본 공급자 OpenAI. 사용자에게 전송 데이터·분석 목적·보관·철회 효과를 알리고 동의 시점/정책 버전을 기록한다. 파일 업로드만으로 동의하지 않는다.
- 서버가 동의를 검증하지 못하면 `EXTERNAL_LLM` 호출 차단(403 `AI_CONSENT_REQUIRED`).
- 기존 비동기 작업 중 동의 철회 후 처리 취소/결과 삭제 범위는 정책 결정 필요. 동의 없이 모델에 전송하도록 자동 폴백하지 않는다.

## 5. 추천·적합도 계약 (#9·#6)

### 5.1 추천 피드

`GET /api/v1/recommendations?page=0&size=20&sort=MATCH_SCORE&onlyActive=true&viewStatus=ALL`

- `sort=MATCH_SCORE | NEWEST | DEADLINE`, `viewStatus=ALL | READ | UNREAD`(기본 ALL), `analysisStatus=ALL | ANALYZED | NOT_ANALYZED`. 미분석 공고도 검색/추천/추가 탐색에서 표시한다. 선호 조건은 사용자 Preferences를 기본 적용한다.
- 프로필이 확정되지 않으면 `409 PROFILE_NOT_CONFIRMED` 등으로 안내. 무근거 점수를 생성하지 않음.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "jobId": 101,
        "title": "백엔드 개발자",
        "companyName": "예시 기업",
        "source": "MANUAL",
        "region": "서울",
        "originalUrl": "https://example.com/jobs/101",
        "match": {
          "status": "READY",
          "score": 87,
          "summary": "프로젝트 기술과 일부 업무가 일치합니다.",
          "strengths": ["Spring Boot 프로젝트", "MySQL 최적화 경험"],
          "gaps": ["상용 웹 운영 경력 확인 필요"],
          "unknowns": ["상세 근무 조건 미제공"],
          "analyzedAt": "2026-11-20T09:20:00+09:00"
        },
        "viewedAt": null,
        "isNew": true
      },
      {
        "jobId": 102,
        "title": "개발자",
        "companyName": "상세정보 없는 기업",
        "source": "SARAMIN",
        "region": null,
        "originalUrl": "https://example.com/jobs/102",
        "match": {
          "status": "INSUFFICIENT_DATA",
          "score": null,
          "summary": "지원자격 상세 정보를 확인할 수 없습니다.",
          "strengths": [],
          "gaps": [],
          "unknowns": ["필수 지원자격 미제공"],
          "analyzedAt": null
        },
        "viewedAt": "2026-11-20T09:30:00+09:00",
        "isNew": false
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "hasNext": false
  }
}
```

- `match.status = NOT_ANALYZED | PENDING | READY | INSUFFICIENT_DATA | BUDGET_EXHAUSTED | FAILED`. AI가 분석하지 않은 공고는 `score=null`이고 사용자는 개별 분석 요청 가능. `viewedAt=null`이면 미확인, 날짜가 있으면 확인, `isNew`는 최근 수집 여부로 별도 개념이다.
- 점수 0~100은 **적합도(채용 요건 일치 정도)**이지 합격 확률이 아니다. 분석 미완료/근거 부족 상태는 `score:null`을 허용.
- `sort=MATCH_SCORE`일 때 숫자 미산출 공고의 하단 배치·표시를 별도 일관 규칙으로 둔다.
- 캐시 키는 `profileVersion + jobVersion + scoringVersion + modelVersion + promptVersion`. 변경된 조합에 한해서만 재분석하고, 채용공고 수집 시 PDF/포트폴리오를 재분석하지 않는다.

### 5.2 적합도 상세

`GET /api/v1/jobs/101/match`

```json
{
  "success": true,
  "data": {
    "jobId": 101,
    "status": "READY",
    "score": 87,
    "scoreMeaning": "채용요건 적합도(합격 확률 아님)",
    "requirements": [
      {
        "category": "MUST",
        "text": "Java/Spring 경험",
        "match": "PARTIAL",
        "reason": "Spring Boot 프로젝트 경험은 있으나 상용 운영 경력은 확인되지 않음",
        "evidence": ["확정 프로필: Spring Boot 팀 프로젝트"]
      },
      {
        "category": "PREFERRED",
        "text": "제조업 이해",
        "match": "MATCH",
        "reason": "제조 장비 SW 업무 경험",
        "evidence": ["확정 프로필: 제조 장비 SW 경력"]
      }
    ],
    "strengths": ["기술스택·제조 경험"],
    "gaps": ["상용 웹 운영 경력 검증 필요"],
    "unknowns": [],
    "profileVersion": 2,
    "jobVersion": 3,
    "scoringVersion": "v1",
    "analyzedAt": "2026-11-20T09:20:00+09:00"
  }
}
```

- `category=MUST | PREFERRED | RESPONSIBILITY`, `match=MATCH | PARTIAL | GAP | UNKNOWN`. 미확인과 불일치 분리. 초기 가중치 담당업무 35/필수 자격 35/우대 20/근무조건 10, MATCH=1/PARTIAL=0.5/GAP=0, UNKNOWN은 불일치와 구분한다.
- 검증 가능한 상세 JD 요건/업무가 2개 이상이며 그중 필수 자격 또는 담당업무가 최소 하나, 확정 프로필이 있어야 점수를 산출. 미충족 시 `score=null`+`INSUFFICIENT_DATA`. 제공되지 않은 범주는 가중치 분모에서 제외하여 재정규화하고 근거 범위를 표시한다. 필수 자격 미충족은 감점 및 경고로 표시하되 공고를 목록에서 삭제하지 않는다.
- 자동 추천 1차 필터는 일반 코드로 수행, 의미 비교는 LLM 활용 가능. 점수는 출력 검증·가중치 정책을 거쳐 산출하며 임의 LLM 숫자를 바로 노출하지 않는다.
- 데이터/동의/예산 미충족일 때 `INSUFFICIENT_DATA` 또는 `FAILED`과 명확한 이유를 표시.

### 5.3 필요 시 재추천

`POST /api/v1/recommendations/refresh`: 프로필 변경이나 재수집으로 점수가 무효화된 경우를 위한 보조 API. 202 `{ "success":true, "data":{ "status":"PENDING", "requestId":"rec-20261120-001" } }` [설계안].

- 사용자가 피드를 열 때마다 반복 실행 금지, 동일한 버전에 대한 중복 요청 억제, 비용/호출 횟수 제한.
- 기본은 서버 자동 재계산이며 수동 새로고침은 선택.

### 5.4 미분석 공고 개별 AI 분석 (#9)

`POST /api/v1/jobs/{jobId}/match/analyses` — 사용자가 'AI 적합도 분석하기'를 눌렀을 때만 호출(이력서 원문 재분석 없음). 결과 캐시가 유효하면 새 LLM 호출 없이 반환. 202 반환 예시:

```json
{"success":true,"data":{"jobId":102,"status":"PENDING","requestId":"job-match-102-01"}}
```

- 사용자당 하루 개별 분석 3건 제한, 자동 분석 회차당 10건·하루 20건 제한, 누적 $5 비용 가드. 같은 조합 분석 중복/재호출 방지.
- 예산이 없으면 `AI_BUDGET_EXHAUSTED` 등 명확한 상태/오류, JD 부족이면 `INSUFFICIENT_DATA`. 결과 조회는 `GET /api/v1/jobs/{jobId}/match`.

### 5.5 공고 자동 확인/미확인 (#6)

- 공고 카드 클릭 → **상세 정보 로드 성공** → `PUT /api/v1/me/jobs/{jobId}/viewed` 실행. 같은 공고를 다시 열어도 최초 `viewedAt`을 보존하는 멱등 처리. 200 반환:

```json
{"success":true,"data":{"jobId":101,"viewedAt":"2026-11-20T09:30:00+09:00"}}
```

- 사용자 선택 시 `DELETE /api/v1/me/jobs/{jobId}/viewed` → 204, 미확인으로 되돌림.
- 추천/목록의 `viewStatus=ALL|READ|UNREAD` 필터는 개인 인증 데이터. 공개 API에서 읽음 여부 노출 금지.
- **GET 공고 상세 자체에 읽음 기록을 섞지 않는다.** 미리보기/프리페치가 읽음으로 오기록되지 않도록 프론트에서 명시적으로 상태 변경 요청.
- `isNew`는 최근 수집 여부로 읽음·AI 분석과 분리하고 공고 수정만으로 읽음을 초기화하지 않는다.

## 6. 관심 공고·지원 기록 (#10)

- `GET /api/v1/me/bookmarks?page=0&size=20`: 내가 저장한 공고 카드 목록.
- `PUT /api/v1/me/bookmarks/{jobId}`: 200/201, 이미 저장한 경우에도 멱등 성공; `DELETE`: 204.
- `GET /api/v1/me/applications?page=0&size=20&status=APPLIED`: 내 지원 기록 목록.
- `POST /api/v1/me/applications`:

```json
{
  "jobId": 101,
  "status": "APPLIED",
  "appliedAt": "2026-11-20",
  "memo": "공식 사이트에서 직접 지원"
}
```

- 201 응답 `{ "success":true, "data":{ "applicationId":501 } }`.
- `GET/PATCH/DELETE /api/v1/me/applications/{applicationId}`: 본인 기록 상세/수정/삭제.
- `status = INTERESTED | APPLIED | DOCUMENT_PASSED | INTERVIEW | OFFER | REJECTED | WITHDRAWN` [설계안].
- 공고 마감/원본 비공개 이후에도 지원 기록을 유지할 수 있도록 참조 정책 수립. 자동 입사지원은 지원하지 않음.

## 7. 인증·삭제·관리 API (#11, #7, #12)

### 7.1 인증

- 카카오 OAuth 시작: `GET /oauth2/authorization/kakao`; 콜백: `GET /login/oauth2/code/kakao`. Spring Security의 실제 설정/세션 방식에 맞춰 변경될 수 있다.
- `GET /api/v1/auth/me`: 200 로그인 사용자 기본 식별 정보, 미로그인 401.
- `POST /api/v1/auth/logout`: 인증 방식에 맞춰 로그아웃. 세션/CSRF/쿠키 설정은 #11 확정.
- `DELETE /api/v1/me/account`: 본인 탈퇴/데이터 삭제 요청. 확인 단계·삭제 범위/기간·되돌릴 수 없는 작업에 대한 정책 확정 후 구현.

### 7.2 자동 수집 운영 (일반 사용자용 아님)

`POST /api/v1/internal/collection-runs`

```json
{ "source": "SARAMIN", "mode": "INCREMENTAL" }
```

202 `{ "success":true, "data":{ "runId":701, "status":"PENDING" } }`.

`GET /api/v1/internal/collection-runs?page=0&size=20`의 항목 예시:

```json
{
  "runId": 701,
  "source": "SARAMIN",
  "status": "SUCCEEDED",
  "startedAt": "2026-11-20T08:00:00+09:00",
  "completedAt": "2026-11-20T08:01:00+09:00",
  "fetched": 30,
  "created": 8,
  "updated": 21,
  "skipped": 1,
  "failed": 0
}
```

- `status=PENDING | RUNNING | SUCCEEDED | PARTIAL | FAILED`. API/운영 UI 어디에도 외부 API 키나 무허가 상세 JD를 노출하지 않음.
- 스케줄 작업이 기본이므로 내부 REST 실행 API는 운영상 실제 필요할 때만 추가한다.

## 8. 후속 AI 기능 (#14~#16)

### 8.1 기업 정보 분석
- `POST /api/v1/jobs/{jobId}/company-analyses`: 사용자 요청에 의해 시작, 202 `analysisId`.
- `GET /api/v1/jobs/{jobId}/company-analyses/{analysisId}`: 상태 및 결과; 결과 항목마다 `sourceUrl`, `checkedAt`, `summary`, `confidence`를 포함하도록 설계. 출처 없는 단정 금지.

### 8.2 자기소개서
- `POST /api/v1/me/cover-letters`:

```json
{
  "jobId": 101,
  "question": "지원 동기",
  "instructions": "사실과 근거만 사용해 주세요."
}
```

- 202 `draftId/status` 또는 201 동기 생성 방식은 #15에서 비용·지연에 따라 확정.
- `GET /api/v1/me/cover-letters`, `GET/PATCH/DELETE /api/v1/me/cover-letters/{id}`: 본인 초안 목록·조회·수정·삭제.
- 사용자 확인하지 않은 경력/프로젝트 성과·수치 생성 금지. 회사/공고 선택 및 명시 요청 없이 자동 생성하지 않음.

### 8.3 분석·모니터링
- 모델/프롬프트 버전, 토큰·시간·성공/실패·비용 추정·재시도 등의 **내부 운영 지표**는 #9/#16에서 기록한다. 원문 이력서·LLM 전체 입력을 일반 로그로 남기지 않는다.
- 외부 LLM 공급자/모델·일별 비용 한도·분석 재사용/배치 정책은 **확정 전**. 임의로 API 키나 모델 이름을 하드코딩하지 않는다.

## 9. 확정한 초기 정책과 외부 의존 조건

### 비용·개인정보·문서
- **OpenAI API로 시작**, 사용자가 보유했다고 밝힌 $5 크레딧 우선 사용(실제 가용/만료 별도 확인), 월 비용 목표 약 1만 원. 초기 애플리케이션 누적 **$5 비용 가드**를 설정하되 실제 청구·환율과 차이가 발생할 수 있으므로 제공업체 예산 제한도 병행한다. **자동 충전/상향 금지**.
- 저비용 모델부터 검증. 모델/토큰 제한·타임아웃은 서버 설정으로 관리하고 일시적 오류 재시도는 1회까지만. 입력·출력 원문과 개인정보는 로그에 저장하지 않음.
- **PDF/DOCX, 10 MiB/파일**. 업로드=`NOT_ANALYZED`, 사용자 버튼 클릭에서만 AI 분석 요청. 진행 `ANALYZING`, 완료 `ANALYZED`, 실패 `FAILED`; 확정 프로필은 사용자 검토/저장 때만 변경. 문서 분석 결과는 DB에 저장/재사용.
- 원본 비공개 보관, 사용자가 삭제/교체하면 원본·추출문·캐시 삭제 및 즉시 접근 차단. 백업은 운영 시 최대 30일 안에 파기하고 복원 시 삭제 기록을 재적용한다.
- 외부 OpenAI 전송은 업체/목적/데이터 범위에 대한 명시적 동의, 연락처·이메일·상세 주소 등 민감정보 마스킹/최소화가 선행되어야 함.

### 수집·추천·점수
- 승인/이용 조건에 부합하는 제공처에서 **6시간마다(Asia/Seoul 00/06/12/18)** 신규/수정 공고 수집. 제공처 한도·허가 조건이 우선. 상세 JD 저장·분석 권한이 없다면 시도하지 않는다.
- **허용된 수집 공고는 모두 검색 가능하게 유지**하며, AI 선별에서 제외해도 버리지 않는다. Java 후보 선별은 보수적이며 일부 추가 탐색 후보도 AI 분석에 배정한다.
- 초기 분석 한도: **수집 회차당 자동 10건, 하루 자동 20건**, 우선 후보 80% / 추가 탐색 후보 20%, 사용자의 개별 분석 하루 3건. $5 비용 가드와 사용량 제한 중 **먼저 도달한 제한이 우선**.
- 점수 초깃값은 담당업무·직무경험 35%, 필수 지원자격 35%, 우대사항 20%, 근무조건 10%. Java 규칙으로 산출하며 LLM 자유 숫자를 그대로 점수로 사용하지 않음.
- 구체 요건이 2개 미만이거나 필수/담당업무 근거가 전혀 없으면 `score=null`, `INSUFFICIENT_DATA`; 우대사항 누락을 불일치로 취급하지 않는다. 점수는 합격 가능성이 아니라 요구조건 적합도다.
- 이미지 JD OCR·불특정 외부 링크 자동 추적은 초기 제외. 포트폴리오 URL은 사용자가 지정한 공개 GitHub 프로젝트 범위의 README 등 허용 자료만 명시적으로 조회하고 SSRF를 방지한다.

### 읽음·인증
- 카드 클릭 후 **상세 화면 로드 성공** 시 프론트가 `PUT /api/v1/me/jobs/{jobId}/viewed` 호출. 이력은 사용자별로 저장하며 `GET` 공고 상세는 부작용 없이 유지.
- `READ`/`UNREAD` 필터·미확인 수, `NEW`(새 수집)/`분석 상태`와의 구분. 기본 읽음 버튼 없음. 필요하면 삭제 API로 미확인 복귀.
- 최초 개발은 **루프백 로컬 단일 사용자**. 공개 배포 전 **카카오 OAuth2·Spring Security 세션** 인증, Secure/HttpOnly/SameSite 쿠키·CSRF·소유권 검증. 인증 전 실제 개인정보를 가진 앱을 외부나 와이파이에 공개하면 안 됨.
- 초기 알림은 웹사이트 내 새 공고/미확인 수만 표시. 모바일 푸시·이메일은 후속.

### 외부 확인만 남은 항목
1. 채용 사이트 API 승인/요청량/상세 JD 취득·저장·LLM 가공 허가.
2. OpenAI 크레딧 유효성·모델 요금·전송 데이터 정책과 실제 청구.
3. 배포 스토리지·백업의 삭제/파기 정책 실제 작동.
4. 모델 분석 품질 및 초기 가중치의 샘플 평가; DB 컬럼/Enum/세부 Validation은 구현 시 확정.

## 10. 구현·검증 원칙

- 상세 JD 없는 공고는 누락 필드를 거짓으로 채우지 않고 `INSUFFICIENT_DATA`를 일관되게 반환한다.
- 외부 데이터 동기화에서 동일 `source+externalId`는 중복 저장하지 않는다. 동일 회사·제목을 이유로 다른 공고를 강제 병합하지 않는다.
- 새로고침·동시 요청이 중복 LLM 비용을 발생시키지 않도록 캐시·멱등 처리·timeout을 고려한다.
- 본인이 아닌 사용자 데이터에 대한 요청은 처리하지 않는다. 외부로 공개할 때는 개인 API 인증/권한 테스트를 반드시 수행한다.
- 프론트는 모바일 우선, 로딩/오류/분석 불충분을 분리하고 추천 사유를 설명한다. 상세 로드 성공 후 읽음 API를 호출하며, 미분석 후보도 노출한다.
- 구현 PR에는 **실제로 호출한 Endpoint 예시와 HTTP 상태, 관련 테스트 결과, 미실행 이유**를 기록한다.
- 설계 변경 시 해당 API 섹션과 [기능 명세서](../020-product/feature-spec.md)를 함께 갱신하며, 서비스 소개 전용 `README.md`에 개발 절차를 넣지 않는다.

## 11. 참고

- [기능 명세서](../020-product/feature-spec.md)
- [Java / Spring 코드 컨벤션](../050-engineering/code-convention.md)
- [사람인 공식 채용공고 API](https://oapi.saramin.co.kr/guide/job-search) / [이용자 주의사항](https://oapi.saramin.co.kr/caution)
- [원티드 OpenAPI](https://openapi.wanted.jobs/) / [잡코리아 API 안내](https://www.jobkorea.co.kr/service/api)

**이 문서에 명시한 `https://example.com` URL, 공고·이력서·퍼센트는 모두 설계 설명을 위한 가상 예시다.**
