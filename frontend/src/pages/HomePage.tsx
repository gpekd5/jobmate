import { BackendHealth } from '../features/backend-health/BackendHealth';

export function HomePage() {
  return (
    <main className="home">
      <p className="eyebrow">로컬 개발환경</p>
      <h1>JobMate</h1>
      <p className="intro">백엔드와 데이터베이스 연결 상태를 확인합니다.</p>
      <BackendHealth />
    </main>
  );
}
