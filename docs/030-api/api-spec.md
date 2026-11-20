# JobMate API 명세서 (정책 합의 v1.2)

> 기준일: 2026-11-20 · 문서 작업: #19 · [기능 명세서](../020-product/feature-spec.md)
>
> **전체 로드맵을 포함하는 API 명세다.** 채용공고 목록·상세·로컬 개발 입력은 #5·#6 작업에서 구현하며 확정 계약은 §3을 따른다. 문서·프로필·추천 API는 #8·#9·#6 작업에서 §4~5대로 구현한다. 읽음·인증 등 그 밖의 경로는 후속 설계다. 외부 채용 사이트 API 경로를 뜻하지 않는다.
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
| AI 상태 | GET | `/api/v1/me/ai-status` | 설정·비용 선점 상태(키 제외) | LOCAL ONLY | #9 |
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
        "lastCollectedAt": "2026-11-20T08:00:00+09:00",
        "jobVersion": 1,
        "updatedAt": "2026-11-20T08:00:00+09:00"
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
    "lastCollectedAt": "2026-11-20T08:00:00+09:00",
    "jobVersion": 1,
    "updatedAt": "2026-11-20T08:00:00+09:00"
  }
}
```

- `detailAvailability`: `AVAILABLE | PARTIAL | UNAVAILABLE | RESTRICTED`.
- `detailSource`: `OFFICIAL_API | PERMITTED_CONTENT | USER_PROVIDED | UNKNOWN`.
- JD 5개 목록 필드의 `null`은 미제공·미확인, `[]`는 출처에서 명시한 해당 없음, 값이 있는 배열은 확인된 내용이다. 없는 요건을 추측해 채우지 않는다. `AVAILABLE`은 5개가 모두 non-null, `PARTIAL`은 일부만 non-null이다. `UNAVAILABLE`·`RESTRICTED`는 모두 `null`이며 `detailExtractedAt`도 `null`이다. 이 조합을 어기는 입력은 400이다.
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

### 3.4 공고 카탈로그 구현 계약 (#5·#6)

- 현재 구현: 공개 로컬 목록·상세, `local` 프로파일에서만 개발용 생성·수정. 추천·적합도는 §5의 별도 개인 API로 제공하며 사용자별 읽음은 후속이다. 공고 GET에는 사용자 상태나 AI 점수를 넣지 않는다.
- `source=MANUAL | SARAMIN | WANTED | JOBKOREA`. 외부 ID는 대소문자를 구분해 고유성을 검사하며 다른 출처의 같은 ID는 별개 공고다. 직무·지역·고용형태는 현재 길이가 제한된 문자열이며 제공처 공통 Enum으로 성급히 변환하지 않는다.
- 검색 `keyword`는 회사명·공고명 부분 일치, `region`·`jobRole`·`source`는 정확 일치. `activeOnly=true`는 명시적 모집 상태와 현재 마감 시각을 함께 반영하며 `false`는 마감 공고도 포함한다. 응답 `active`도 같은 유효 모집 상태다.
- `NEWEST`: `postedAt` 내림차순(미확인 날짜 뒤), ID 내림차순. `DEADLINE`: 마감 시각 오름차순(미확인 날짜 뒤), ID 내림차순. 추천 점수순을 대신하지 않는다.
- 목록·상세는 `jobVersion`과 `updatedAt`을 함께 반환한다. 공고의 `jobVersion`은 최초 1에서 내용 변경 시 증가하고 `updatedAt`은 그 변경 시각이다. `lastCollectedAt`은 마지막 입력·수집 확인 시각이며 사용자 READ/UNREAD와 무관하다. 같은 출처의 외부 ID 갱신은 기존 ID를 보존한다. 내용이 같고 `detailExtractedAt` 같은 확보 시각만 바뀌면 버전·수정 시각은 유지하고 확보·마지막 확인 시각만 갱신하며, 더 오래된 수집 결과로 최신 내용을 덮어쓰지 않는다.
- 개발용 POST는 `source=MANUAL`, 외부 ID 없음으로 생성한다. `active` 생략 시 true, `detailSource` 생략 시 USER_PROVIDED이며 `detailAvailability` 생략 시 JD 5개 필드의 null 여부에서 AVAILABLE/PARTIAL/UNAVAILABLE을 결정한다. PATCH에서 JD 확보 범주가 바뀌면 `detailAvailability`도 함께 변경해 정합성을 유지한다. PATCH는 생략한 필드 유지, 선택 필드의 명시적 `null`은 제거, JD 목록의 `[]`는 명시적 해당 없음이다. 회사명·공고명·모집 상태 등 필수값에 `null`을 지정하면 400. 출처·외부 ID는 개발 API에서 변경하지 않는다.
- 입력 길이: 회사명 200자, 제목 300자, 직무·지역·고용형태 100자, 경력·학력·급여 텍스트 300자, 원문 URL 2000자. JD 각 목록은 최대 100항목이며 항목당 공백이 아닌 2000자 이하다. 페이지는 0 이상·크기는 1~100이며 JPA offset 범위를 넘는 요청도 400으로 거절한다.
- 원문 URL은 입력 시 HTTP(S)만 허용하며 서버가 URL을 다운로드하지 않는다. 예시 공고는 자체 작성 데이터이고 실제 기업·제공처 공고가 아니다.
- 오류: 형식·Bean Validation은 400 `INVALID_REQUEST`, 잘못된 공고 정책·PATCH 필드·페이지 범위는 400 `INVALID_JOB_INPUT`, 미확보/이용 제한 상세 내용 저장은 400 `RESTRICTED_JOB_CONTENT`, 없는 공고는 404 `JOB_NOT_FOUND`, 중복·동시 수정 충돌은 409 `JOB_CONFLICT`다. 원문 값과 내부 DB 오류를 오류 응답에 노출하지 않는다.
- 전체 공고 API는 개인 AI 상태를 반환하지 않는다. 적합도 상태는 §5에서 조회한다. 상세 정보가 없는 상태와 AI가 분석해 근거가 부족한 상태를 혼동해 `INSUFFICIENT_DATA` 결과를 만들어내지 않는다.

## 4. 사용자 문서·포트폴리오·프로필 계약 (#8 구현)

모든 개인 API는 `local` 프로파일·루프백 접속에서만 제공한다. 고정 로컬 소유자 1을 서버가 결정하며 클라이언트의 사용자 ID를 받지 않는다. 비로컬 프로파일은 404, 외부 주소·Host·Origin은 403 `LOCAL_ONLY`. 개인 응답은 `Cache-Control: no-store`다. 인증·다중 사용자는 #11 후속이다.

### 4.1 문서와 링크

- `POST /api/v1/me/documents`: multipart `type=RESUME|PORTFOLIO`, `file`; 201 반환 `data={documentId,type,fileName,analysisStatus,uploadedAt}`. 업로드만으로 AI를 호출하지 않는다.
- `GET /api/v1/me/documents`: `data`는 위 메타데이터의 배열. 원본 다운로드·저장 경로·추출 원문은 제공하지 않는다.
- `DELETE /api/v1/me/documents/{id}`: 204. 관련 초안·확정 프로필·추천 캐시를 무효화하고 원본 삭제를 처리한다. 파일 삭제 실패는 비공개 삭제 티켓에 보존해 재시도한다.
- PDF/DOCX만 10 MiB 이하. 확장자·MIME·시그니처·파싱을 검사하고 암호화·손상·매크로·위험한 PDF action/첨부·DOCX 외부 관계·과도한 압축을 거절한다. 보수적 검증으로 DOCX의 일반 외부 하이퍼링크도 거절한다. 이미지 PDF OCR은 지원하지 않는다.
- RESUME는 새 업로드로 교체된다. DB 커밋 후 이전 원본을 지우며 새 프로필 확정 전까지 기존 확정 프로필·추천은 유지한다. 명시적인 문서/링크 삭제는 관련 개인 결과를 무효화한다.
- `POST /api/v1/me/portfolio-links`: `{url,description}` → 201 `{linkId,url,description}`. GET은 배열, DELETE `/{id}`는 204.
- 링크는 `https://github.com/{owner}/{repo}` 공개 저장소만 허용한다. 등록만으로 조회하지 않는다. 분석 요청에서 선택한 링크의 `raw.githubusercontent.com/{owner}/{repo}/HEAD/README.md`만 5초/1 MiB 한도로 읽는다. 리다이렉트·로그인·비공개 저장소·다른 파일 탐색은 없다.

### 4.2 명시적 분석과 초안 검토

`POST /api/v1/me/profile/analyses` → 202. 다음 요청 전에 별도의 PROFILE_EXTRACTION 동의가 필요하다.

```json
{"documentIds":[14],"portfolioLinkIds":[],"analysisMode":"EXTERNAL_LLM"}
```

- 이력서 포함 문서 1~10개, 선택 링크 최대 5개. 지원 모드는 EXTERNAL_LLM만이다.
- 응답/`GET /api/v1/me/profile/analyses/{id}`의 `data`는 `{analysisId,status,result,errorCode,createdAt,completedAt,documentIds,portfolioLinkIds}`. `status=PENDING|RUNNING|SUCCEEDED|FAILED`; 실패 결과는 null과 안전한 코드다.
- 문서 상태는 `NOT_ANALYZED|ANALYZING|ANALYZED|FAILED`. PENDING 작업은 서버 비동기로 실행한다. 중복 진행 요청은 409, 실패 후 명시적 새 요청으로 재시도한다. 시작·응답 저장 전 동의/원본/삭제 epoch를 확인하고 삭제·철회 중 도착한 결과는 폐기한다. 재시작 시 중단 작업은 실패 처리한다.
- 연락처·이메일·식별번호·라벨이 있는 이름/주소·일반적인 한국 도로명주소를 마스킹한다. 정규식 마스킹은 모든 형태를 보장하지 않으므로 업로드 전에 불필요한 민감정보를 제거한다. 원문은 분석 시 메모리에서만 추출하고 DB/로그에 저장하지 않는다.
- `result`는 다음 5개 배열이며 자동 확정하지 않는다: `workExperiences`, `skills`, `projects`, `education`, `certifications`. 각 항목은 `{name,context,summary,evidence,confidence}`. evidence는 입력에서 확인되는 인용만 수락한다.
- 문서만 선택한 요청은 같은 문서 ID 집합·삭제 epoch·동의 epoch·모델·프롬프트 버전의 성공 결과를 재사용한다. 이 경우 유료 호출·새 비용 예약이 없다. 원격 README는 내용 버전이 바뀔 수 있으므로 링크 포함 요청은 명시적 요청마다 다시 읽고 분석한다. 응답의 `modelVersion`·`promptVersion`으로 분석 버전을 구분한다.
- `context=WORK|TEAM_PROJECT|PERSONAL_PROJECT|TRAINING_PROJECT|LEARNING|UNKNOWN`, `confidence=HIGH|MEDIUM|LOW|UNKNOWN`. 프로젝트·학습을 상용 실무로 승격하거나 미기재 경력을 추정하지 않는다.

### 4.3 확정 프로필·희망조건

`GET /api/v1/me/profile`의 초기 `data`는 `{profileVersion:0,confirmed:false,workExperiences:[],skills:[],projects:[],education:[],certifications:[]}`다.

`PUT /api/v1/me/profile`은 GET의 **현재 profileVersion 필수**, `confirmed:true`와 위 5개 배열로 전체 교체한다. 검토자가 초안을 수정할 수 있다. 각 사실의 이름/맥락/요약/근거/신뢰도를 보존한다. 성공은 200, 서버가 현재 확정 프로필을 저장하고 버전을 증가한다. 과거 확정본 복원 기능은 없다. 오래된 버전은 409 `PROFILE_CONFLICT`. 문서 교체는 확정 프로필을 자동 변경하지 않는다.

`GET/PUT /api/v1/me/preferences`:

```json
{"desiredRoles":["BACKEND"],"regions":["서울"],"employmentTypes":["FULL_TIME"],"minSalary":null,"avoidDispatch":true,"avoidFrequentTravel":false}
```

모두 선택이며 미입력은 제한 없음이다. `minSalary`는 연봉 원(KRW) 단위다. 직무/지역/고용형태는 공고와 같은 문자열이다. 선호조건 변경도 프로필 버전을 증가시켜 캐시를 분리한다. 확인되지 않은 공고 조건으로 강제 탈락시키지 않는다.

### 4.4 동의와 실행 가능 상태

- `GET/PUT /api/v1/me/ai-consent`: 요청 `{provider:"OPENAI",purposes:["PROFILE_EXTRACTION","JOB_MATCHING"],consented:true}`. 목적은 부분 선택 가능하다. 응답은 동일 필드와 서버 `consentVersion`. 동의 시각/정책 버전도 DB에 기록한다.
- 동의 철회/범위 변경은 epoch를 증가시키고 진행 결과 저장·이전 epoch 캐시 재사용을 차단한다. 철회가 이미 전송된 요청을 공급자에서 되돌리지는 못한다. 명시적 원본 삭제는 관련 저장 결과도 삭제한다.
- `GET /api/v1/me/ai-status`: `{available,reasonCode,model,budgetLimitUsd,reservedUsd}`. 키는 반환하지 않는다. 동의 여부는 별도 동의 API로 확인한다. 이 GET도 AI 호출이 없다.
- 동의 없음 403 `AI_CONSENT_REQUIRED`, 설정 없음 503 `AI_NOT_CONFIGURED`, 예산 소진 429 `AI_BUDGET_EXHAUSTED`. 어떤 경우에도 외부 AI로 자동 폴백하지 않는다.

## 5. 추천·적합도 계약 (#9·#6 구현)

### 5.1 조회

`GET /api/v1/recommendations?page=0&size=20&sort=MATCH_SCORE&onlyActive=true&analysisStatus=ALL`

- `sort=MATCH_SCORE|NEWEST|DEADLINE`, `analysisStatus=ALL|ANALYZED|NOT_ANALYZED`, page≥0, size 1~100. `viewStatus`는 ALL만 허용한다. READ/UNREAD·viewedAt·NEW는 #11과 함께 후속 구현하며 현재 반환하지 않는다.
- 미확정 프로필 409 `PROFILE_NOT_CONFIRMED`, 추천 목적 동의 없음 403. 키·예산이 없어도 동의·프로필이 있으면 저장 결과/미분석 피드를 조회할 수 있다. 조회는 유료 호출을 하지 않는다.
- `data={items,page,size,totalElements,totalPages,hasNext}`. 각 item은 공고 요약과 `match`를 포함한다. 분석되지 않은 공고를 숨기지 않는다. MATCH_SCORE는 숫자 내림차순, null 뒤, 게시일/ID 내림차순으로 안정 정렬한다. 기존 `/jobs` 검색은 그대로 유지한다.
- `GET /api/v1/jobs/{jobId}/match`와 피드의 `match`는 같은 계약이다:

```json
{"jobId":101,"status":"NOT_ANALYZED","score":null,"scoreMeaning":"채용요건 적합도(합격 확률 아님)","summary":"아직 분석하지 않은 공고입니다.","requirements":[],"strengths":[],"gaps":[],"unknowns":[],"evidenceCount":0,"evaluatedCategories":[],"profileVersion":2,"jobVersion":1,"scoringVersion":"v1","modelVersion":"UNCONFIGURED","promptVersion":"job-match-v2","analyzedAt":null,"errorCode":null}
```

상태는 `NOT_ANALYZED|PENDING|READY|INSUFFICIENT_DATA|BUDGET_EXHAUSTED|FAILED`. 실제 설정/정책 버전은 서버 응답을 사용한다. 점수 없는 상태에서 0% 등 가짜 값을 표시하지 않는다. `requirements` 항목은 `{id,category,text,match,reason,evidence}`이며 근거는 확정 프로필의 실제 사실로 서버가 매핑한다.

### 5.2 점수와 근거

- `category=RESPONSIBILITY|MUST|PREFERRED|WORK_CONDITION`, `match=MATCH|PARTIAL|GAP|UNKNOWN`.
- 가중치 **35/35/20/10**. MATCH=1, PARTIAL=0.5, GAP=0. UNKNOWN은 0점으로 치환하지 않고 해당 평가에서 제외한다. 제공되지 않은 범주도 분모에서 제외하고 남은 범주를 재정규화한다.
- 중복을 제거한 구체 JD 요건이 최소 2개이고 담당업무/필수요건이 하나 이상이어야 한다. HIGH/MEDIUM의 비어 있지 않은 프로필 근거로 확인된 비교도 최소 2개(담당업무/필수요건 포함)여야 숫자를 산출한다. 부족하면 `score:null`, `INSUFFICIENT_DATA`.
- 모델은 숫자를 만들지 않는다. 요구조건 ID별 분류/이유/프로필 근거 ID만 구조화해서 반환하고 Java가 ID·근거·분류를 검증해 점수를 계산한다. 존재하지 않는 근거는 거절하고 근거 없는 비교는 UNKNOWN이다. C# 실무/교육 프로젝트를 Java 상용 실무 MATCH로 과장하지 않는다.
- 미확인과 불일치를 분리하며 필수요건 GAP/PARTIAL은 경고로 표시한다. 근거 수와 평가 범위를 함께 보여준다. 점수는 합격 확률이 아니다.
- 캐시는 `owner+profileVersion+jobVersion+consentVersion+scoringVersion+modelVersion+promptVersion` 고유키다. 버전 변경 뒤 이전 점수를 현재 값처럼 제공하지 않는다.

### 5.3 비동기 분석·제한

- `POST /api/v1/jobs/{jobId}/match/analyses` → 202 `{jobId,status,requestId}`. 동일 조합 진행/완료는 재사용하며 실패 뒤에는 명시적 재요청이 가능하다. 불충분한 JD/프로필은 AI 없이 INSUFFICIENT_DATA로 처리한다.
- `POST /api/v1/recommendations/refresh` → 202 `{jobId:null,status:"PENDING"|"UNCHANGED",requestId}`. 피드 GET마다 호출하지 않는다.
- 로컬 스케줄러는 60초마다 모집 중이며 현재 버전 캐시가 없는 공고를 먼저 필터링하고 최신 200개 후보를 확인한다. #7 수집 회차가 없으므로 현재는 이 갱신 1회를 회차로 취급한다. 회차 최대 10건, 자동 일 20건, 선호 우선 80%/추가 탐색 20%(부족 슬롯 재배정), 개별 일 3건. 일 경계는 Asia/Seoul다. 전체 검색/개별 분석으로 자동 후보에서 빠진 공고도 접근 가능하다.
- DB 원자 예약·고유키·작업 claim으로 동시 중복 호출을 막는다. 비동기 작업은 동의·프로필·공고 버전을 실행 전후 재검증한다. 재시작 중단 작업을 실패 처리하고 무제한 자동 재시도하지 않는다.
- 공통 OpenAI 게이트웨이가 호출마다 최대 입력 바이트/출력 토큰과 명시된 단가로 비용을 선점한다. 누적 상한은 **최대 $5**다. 타임아웃·실패도 보수적으로 선점액을 환급하지 않고, 일시적 429/5xx/연결 오류만 1회 재시도하며 재시도 비용도 별도 선점한다. 실청구 API가 아니며 설정한 단가의 정확성과 공급자 별도 제한을 확인해야 한다.
- 모델·키·가격을 명시하기 전 기본 비활성화다. Responses API `store:false`, strict JSON schema, 고정 OpenAI HTTPS 주소/타임아웃을 사용한다. 키/원문/전체 응답을 로그에 남기지 않는다.


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
- 외부 LLM 공급자와 비용/재사용 정책은 §5·§9 기준이며 모델 이름/단가는 실행 설정으로 지정한다. 임의로 API 키나 모델 이름을 하드코딩하지 않는다.

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
