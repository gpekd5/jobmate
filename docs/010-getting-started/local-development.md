# 로컬 개발환경

MySQL만 Docker로 실행하고 Spring Boot와 React/Vite는 호스트에서 실행한다. 채용공고 테이블·마이그레이션과 서비스 기능은 후속 Issue에서 구현한다.

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

Java 17 / Spring Boot 3.5.16을 사용한다. Spring은 `jdbc:mysql://127.0.0.1:${DB_PORT}/${DB_NAME}`와 `DB_USER`, `DB_PASSWORD`를 사용한다. JPA는 `ddl-auto: validate`, Flyway는 활성화하며 이 단계에서는 도메인 테이블·마이그레이션을 만들지 않는다. Flyway의 스키마 이력 테이블은 도구가 관리한다.

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

브라우저에서 [JobMate 로컬 화면](http://localhost:5173)을 연다. 확인 중 상태 이후 실제 백엔드 응답에 따라 연결 성공 또는 실패가 표시된다. 실패 시 재시도할 수 있다. Vite는 `/actuator`와 `/api`를 `http://localhost:8080`으로 전달하므로 프론트엔드에 별도 DB 비밀번호나 CORS 우회 설정이 필요하지 않다. Vite 개발 프록시는 개발 서버 전용이다.

백엔드를 Ctrl+C로 중지한 뒤 화면에서 재시도하면 실패 상태가 나타나야 한다. 백엔드를 다시 시작하고 재시도하면 성공으로 돌아와야 한다.

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
