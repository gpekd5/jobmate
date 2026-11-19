import { useEffect, useState } from 'react';
import { fetchBackendHealth } from './api';

type ConnectionState =
  | { status: 'checking' }
  | { status: 'connected' }
  | { status: 'failed'; message: string };

export function BackendHealth() {
  const [attempt, setAttempt] = useState(0);
  const [connection, setConnection] = useState<ConnectionState>({
    status: 'checking',
  });

  useEffect(() => {
    const controller = new AbortController();

    fetchBackendHealth(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          setConnection({ status: 'connected' });
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setConnection({
            status: 'failed',
            message:
              error instanceof Error && error.name === 'TimeoutError'
                ? '응답 시간이 초과되었습니다. 백엔드 실행 상태를 확인해 주세요.'
                : '백엔드 또는 데이터베이스에 연결할 수 없습니다. 실행 상태를 확인해 주세요.',
          });
        }
      });

    return () => controller.abort();
  }, [attempt]);

  function retry() {
    setConnection({ status: 'checking' });
    setAttempt((previous) => previous + 1);
  }

  return (
    <section className="health-panel" aria-labelledby="health-title">
      <h2 id="health-title">Backend 연결 상태</h2>
      <div role="status" aria-live="polite" aria-atomic="true">
        {connection.status === 'checking' && (
          <p className="connection checking">확인 중…</p>
        )}
        {connection.status === 'connected' && (
          <>
            <p className="connection connected">연결 성공</p>
            <p>백엔드와 데이터베이스가 정상 응답했습니다. (UP)</p>
          </>
        )}
        {connection.status === 'failed' && (
          <>
            <p className="connection failed">연결 실패</p>
            <p>{connection.message}</p>
          </>
        )}
      </div>
      <button disabled={connection.status === 'checking'} onClick={retry}>
        {connection.status === 'failed' ? '다시 시도' : '다시 확인'}
      </button>
    </section>
  );
}
