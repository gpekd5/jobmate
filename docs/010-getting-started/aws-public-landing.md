# AWS 공개 소개 페이지 배포 (채용 API 신청용)

작업 Issue: [#21](https://github.com/gpekd5/jobmate/issues/21). 서비스 전체 배포는 [#12](https://github.com/gpekd5/jobmate/issues/12).

## 목적

JobMate는 실제 서비스 운영을 목표로 하지만, 현재 인증/민감정보 저장 기능이 완성되지 않았으므로 **API 신청에 제출할 HTTPS 공개 URL만 먼저 확보**한다. 정적 `landing/index.html`을 AWS Amplify Hosting에 게시하며, Spring Boot·MySQL·OpenAI는 배포/연결하지 않는다. 비공개 파일, 이력서, API 키 등을 넣지 않는다.

## 배포 전 확인

- AWS 계정의 예상 요금·무료 혜택 적용 여부를 [Amplify 공식 요금](https://aws.amazon.com/amplify/pricing/)에서 확인한다. 계정/사용량에 따라 **과금 가능**.
- AWS Billing에서 예산 경고를 설정한다. Budget 경고는 서비스 비용을 자동으로 차단하지 않는다는 점에 유의한다.
- 별도 도메인 구입, EC2/RDS, 사용자 업로드, 데이터 수집 API, WAF 유료 부가기능은 만들지 않는다.
- GitHub [PR](https://github.com/gpekd5/jobmate/pulls)을 먼저 Merge한 뒤, 기본 `main`의 해당 페이지를 배포한다.

## Amplify 콘솔 순서

1. [AWS Amplify 콘솔](https://console.aws.amazon.com/amplify/apps)에서 **Create new app → Deploy an app → GitHub**를 선택한다(콘솔 UI 명칭은 변경 가능).
2. GitHub 인증 후 **`gpekd5/jobmate` 저장소 / `main` 브랜치**를 선택한다.
3. 저장소 루트의 `amplify.yml` 설정을 사용한다. 이 배포는 repo 루트의 **`landing/` 정적 파일만** 배포하도록 구성되어 있다. 콘솔에서 수동으로 설정해야 한다면 build command는 `test -f landing/index.html`, output directory는 `landing`으로 설정한다.
4. 추가 백엔드 생성·유료 옵션은 선택하지 않는다. 설정·요금 안내를 검토하고 사용자가 직접 배포를 승인한다.
5. 배포 완료 후 콘솔에 표시된 실제 `https://....amplifyapp.com` 주소를 복사한다. **주소는 AWS에서 배포될 때 생성되며 미리 정하지 않는다.**
6. 모바일과 데스크톱에서 열어 HTTPS 및 '개발 중' 안내 표시 확인.
7. 사람인 API 신청에는 **실제 방문 가능한 HTTPS URL**과 프로젝트 이용목적을 제출한다. URL만으로 API 승인이 보장되는 것은 아니다.

## 사람인 신청 설명 예시

> JobMate는 사용자의 이력서와 포트폴리오를 분석하고, 허용된 채용공고의 담당업무·지원자격·우대사항과 비교해 AI 기반 맞춤 공고를 추천하는 개인 개발 프로젝트입니다. 현재는 서비스 소개 페이지를 공개했으며, 상세 기능은 개발 중입니다. 사람인 공식 API가 제공하는 범위와 이용 조건에 따라 채용공고를 수집·출처 표기·원문 링크로 안내하고자 합니다. API에서 제공되지 않거나 허용되지 않은 상세 본문은 임의로 수집하지 않습니다.

## 추후 실제 서비스 공개 전

- 개인 문서 업로드·AI 분석·추천 결과 API는 **카카오 로그인·Spring Security 및 파일 소유권 검증 완료 전까지 공개하지 않는다** (#11).
- 실제 운영 시 정적 소개 페이지를 React/Vite 빌드 결과로 변경하고, Spring Boot API/DB는 별도 안전한 운영 환경으로 구성한다 (#12). 현재 `amplify.yml`은 **소개 페이지 전용**이므로 바꿔야 한다.
- 도메인/URL을 계속 재활용할지는 실제 서비스 준비 후 결정. 자동 GitHub 배포로 `main` 커밋이 공개된다.
