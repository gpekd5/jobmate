# 로컬 개발환경

MySQL만 Docker로 실행하고 Spring Boot와 React/Vite는 호스트에서 실행한다. 공고 카탈로그의 예시 입력·조회와 마이그레이션 정책은 이 문서 후반을 따른다.

## 선행 도구

- Java 17 JDK: `java -version`. Mac에서 다른 JDK가 기본이면 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- Docker Desktop과 Docker Compose: Docker Desktop을 실행하고 `docker version`, `docker compose version`으로 확인한다.
- Node.js 26 LTS와 npm: 프론트엔드의 `.nvmrc`와 `package.json` 기준을 따른다. nvm 사용 시 `cd frontend && nvm install && nvm use`.
- Gradle 8.14.5는 별도 설치하지 않고 커밋된 `backend/gradlew` Wrapper를 사용한다.

## 최초 설정과 DB 시작

저장소 루트에서 실행한다.

```bash
cp .env.example .env
# .env의 DB_PASSWORD와 DB_ROOT_PASSWORD를 개인 로컬 값으로 수정
# 기존 .env가 있다면 복사해서 덮어쓰지 않는다.
docker compose config --quiet
docker compose up -d --wait db
docker compose ps
```

`.env`는 Git에서 제외한다. `DB_NAME=jobmate`, `DB_USER=jobmate`, `DB_PORT=3307`이 기본 접속 계약이다. root와 분리된 앱 계정을 사용하며, DB는 `127.0.0.1:3307`에서만 접근할 수 있다. healthcheck는 앱 계정으로 지정 DB에 연결해 `SELECT 1`을 실행하므로 healthy가 된 후 백엔드를 시작한다.

Compose와 셸에서 같은 값을 읽을 수 있도록 `.env`는 `KEY=value` 형식으로 작성한다. 공백이나 `$` 등 특수문자가 있는 비밀번호는 작은따옴표로 감싸고, 셸 명령이나 변수 확장을 넣지 않는다. 외부에서 전달받은 신뢰할 수 없는 파일을 source하지 않는다. 이미 export된 DB 변수가 있으면 Compose에서 `.env`보다 우선하므로, 값을 변경한 뒤에는 같은 터미널에서 아래 주입 명령을 다시 실행한다.

## 백엔드: 별도 터미널

Compose는 루트 `.env`를 자동으로 읽지만 호스트 Spring Boot와 Gradle은 읽지 않는다. **저장소 루트에서 매 터미널마다** 환경변수를 주입한다. 비밀번호를 별도의 Spring 설정 파일에 복사하지 않는다.

```bash
set -a
source .env
set +a
cd backend
./gradlew test
./gradlew build
./gradlew bootRun
```

Java 17 / Spring Boot 3.5.16을 사용한다. Spring은 `jdbc:mysql://127.0.0.1:${DB_PORT}/${DB_NAME}`와 `DB_USER`, `DB_PASSWORD`를 사용한다. JPA는 `ddl-auto: validate`, Flyway를 활성화하고 공고 V1 마이그레이션을 적용한다. 스키마 이력 테이블은 Flyway가 관리한다.

다른 터미널에서 확인한다.

```bash
curl -i http://localhost:8080/actuator/health
```

DB 연결을 포함한 상태가 정상이면 HTTP 200, `{"status":"UP"}`를 반환한다. 최소 Spring 통합 테스트도 실제 MySQL이 필요하다. DB 없이 성공하도록 대체하거나 비활성화하지 않는다. `/actuator/health`는 초기 로컬 개발용이며 공개 배포 시 접근 범위와 정보 노출을 재검토한다.

## 프론트엔드: 별도 터미널

```bash
cd frontend
npm ci
npm run lint
npm run format:check
npm test
npm run build
npm run dev
```

브라우저에서 [JobMate 로컬 화면](http://localhost:5173)을 연다. 실제 공고 API의 목록과 상세를 표시하며 로딩·빈 결과·실패·재시도 상태를 제공한다. Vite는 `/actuator`와 `/api`를 `http://localhost:8080`으로 전달하므로 프론트엔드에 별도 DB 비밀번호나 CORS 우회 설정이 필요하지 않다. Vite 개발 프록시는 개발 서버 전용이다.

백엔드를 Ctrl+C로 중지한 뒤 공고를 다시 조회하면 실패 상태가 나타나야 한다. 백엔드를 다시 시작하고 재시도하면 공고가 다시 조회되어야 한다.

## 중지·재실행과 데이터 보존

- Spring Boot와 Vite는 각 터미널에서 Ctrl+C로 중지한다.
- 저장소 루트의 `docker compose stop db`는 DB를 중지한다. `docker compose up -d --wait db`로 재실행한다.
- `docker compose down`도 named volume `jobmate_mysql-data`를 보존한다. `down -v`는 데이터를 삭제하므로 일반 중지 명령으로 사용하지 않는다.
- 재실행할 때도 백엔드 터미널에서 `.env`를 주입하고 위 실행 명령을 사용한다. 의존성이 바뀌면 `npm ci`를 다시 실행한다.
- MySQL 초기 DB·계정·비밀번호 설정은 **비어 있는 볼륨의 최초 초기화에만** 적용된다. 기존 볼륨에서 `.env`만 바꿔도 DB 계정이 갱신되지 않는다. 기존 접속값을 복원하거나 데이터를 보존하면서 DB 계정 설정을 직접 맞춘다.

## 문제 해결

- 포트 충돌: `lsof -nP -iTCP:3307 -iTCP:8080 -iTCP:5173 -sTCP:LISTEN`으로 확인한다. 다른 프로세스를 임의 종료하지 않는다. DB 포트는 `.env`의 `DB_PORT`를 바꾸고 DB를 다시 생성한 뒤 환경변수를 다시 주입한다. 기본 Spring/Vite 포트를 바꾸면 프록시와 실행 문서도 함께 맞춘다.
- DB unhealthy: `docker compose ps`, `docker compose logs --tail=100 db`로 최초 초기화와 인증 실패를 확인한다. Docker 미실행, 포트 충돌, 기존 볼륨의 이전 계정값을 먼저 확인한다.
- Spring DB 연결 실패: DB가 healthy인지, 같은 셸에서 `.env`를 source했는지, Java 17인지 확인한다. `127.0.0.1:3307`은 호스트 접속 주소이며 컨테이너 내부 포트는 3306이다.
- 환경변수 누락: `.env`를 저장소 루트에 두고 `set -a; source .env; set +a`를 다시 실행한다. 실제 비밀번호가 출력되는 `docker compose config` 결과를 공유하지 않는다.
- 프론트 연결 실패: 먼저 8080 health URL을 curl로 확인한다. 백엔드 정상 후 Vite 터미널의 프록시 오류와 브라우저 네트워크 요청을 확인하고 재시도한다.

환경변수 처리 근거: [Docker Compose 변수 보간](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

## 공고 카탈로그 실행 (#5·#6)

환경변수·DB 시작은 위와 같다. 읽기 API는 기본 실행에서도 제공하지만 개발용 입력은 아래 **local 프로파일**에서만 활성화한다. 서버는 루프백에 바인딩한다. 운영에서 local 프로파일을 사용하지 않는다.

```bash
# 저장소 루트에서, 별도 백엔드 터미널
set -a
source .env
set +a
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

다른 터미널의 **저장소 루트**에서 자체 예시 공고를 입력한다. 실제 채용사이트를 수집하거나 타사 공고를 복사하지 않는다.

```bash
curl --fail-with-body -sS -X POST http://127.0.0.1:8080/api/v1/dev/jobs \
  -H 'Content-Type: application/json' \
  --data-binary @docs/010-getting-started/examples/jobs/available.json
curl --fail-with-body -sS -X POST http://127.0.0.1:8080/api/v1/dev/jobs \
  -H 'Content-Type: application/json' \
  --data-binary @docs/010-getting-started/examples/jobs/partial.json
curl --fail-with-body -sS -X POST http://127.0.0.1:8080/api/v1/dev/jobs \
  -H 'Content-Type: application/json' \
  --data-binary @docs/010-getting-started/examples/jobs/unavailable.json
curl --fail-with-body -sS -X POST http://127.0.0.1:8080/api/v1/dev/jobs \
  -H 'Content-Type: application/json' \
  --data-binary @docs/010-getting-started/examples/jobs/closed.json
```

각 응답의 `data.jobId`로 상세를 확인한다. 수동 입력은 매번 새 ID를 생성한다. 이미 입력한 공고는 같은 POST를 반복하는 대신 `PATCH /api/v1/dev/jobs/{jobId}`로 필요한 필드만 변경한다. 예: `{"title":"수정한 예시 공고"}`. 마감 공고는 목록 기본값에서 제외되고 `activeOnly=false`로 함께 검색할 수 있다. 사용자용 등록 화면은 제공하지 않는다.

```bash
curl --fail-with-body -sS 'http://127.0.0.1:8080/api/v1/jobs?keyword=Java&size=20'
curl --fail-with-body -sS 'http://127.0.0.1:8080/api/v1/jobs?activeOnly=false&sort=DEADLINE'
# 실제 반환 ID로 바꿔서 실행
curl --fail-with-body -sS http://127.0.0.1:8080/api/v1/jobs/1
```

프론트는 위 `npm run dev`로 실행하고 목록·검색·필터·상세를 확인한다. S23 크기 기준 모바일 화면에서 가로 넘침 없이 터치·스크롤할 수 있어야 한다. 모든 공고의 AI 상태는 미분석이며, 추천순 정렬·점수·사용자별 읽음·AI 호출은 이번 범위가 아니다.

### 테스트 데이터 격리

통합 테스트는 실제 MySQL을 사용한다. 개발 공고와 분리하려면 처음 한 번 DB 관리자 셸에서 전용 DB와 앱 계정 권한을 준비한다.

```bash
docker compose exec db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
```

MySQL 셸에서 실행한다. 아래 `jobmate` 계정명은 개인 `.env`의 `DB_USER`와 맞춘다. 계정 비밀번호를 문서나 명령 이력에 적지 않는다.

```sql
CREATE DATABASE IF NOT EXISTS jobmate_catalog_test CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON jobmate_catalog_test.* TO 'jobmate'@'%';
EXIT;
```

```bash
# 저장소 루트에서
set -a
source .env
set +a
cd backend
DB_NAME=jobmate_catalog_test ./gradlew test --rerun-tasks
DB_NAME=jobmate_catalog_test ./gradlew build
```

### Flyway 변경·복구 정책

- 앱 시작·통합 테스트 때 `V1`이 공고 스키마를 생성하며, JPA는 계속 `ddl-auto: validate`로 매핑을 검증한다. 예시 공고는 마이그레이션에 자동 삽입하지 않는다.
- 적용한 V1 파일을 수정하거나 이력 행·볼륨을 지우는 방식으로 되돌리지 않는다. 이후 수정은 V2 이상의 전진 마이그레이션으로 추가한다.
- 이 저장소는 자동 down/Undo 마이그레이션을 제공하지 않는다. 코드 되돌림만으로 DB 구조가 되돌아가지 않는다. 복구가 필요하면 쓰기를 멈추고 백업 복원 또는 데이터 보존을 확인한 보정 마이그레이션을 선택한다.
- MySQL DDL은 실패 시 전체가 자동 롤백된다고 가정하지 않는다. 적용 실패 시 DB와 Flyway 이력을 점검한 뒤 원인을 수정하고 재개한다. `clean`, `down -v`, 임의 `DROP TABLE`로 오류를 숨기지 않는다.

정책 근거: [Flyway 버전 마이그레이션](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html), [마이그레이션·트랜잭션](https://documentation.red-gate.com/flyway/flyway-concepts/migrations).
