export interface HealthResponse {
  status: 'UP';
}

export async function fetchBackendHealth(
  signal: AbortSignal,
): Promise<HealthResponse> {
  const response = await fetch('/actuator/health', {
    signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
  });

  if (!response.ok) {
    throw new Error(`백엔드가 HTTP ${response.status}을 반환했습니다.`);
  }

  const health: unknown = await response.json();
  if (
    typeof health !== 'object' ||
    health === null ||
    !('status' in health) ||
    health.status !== 'UP'
  ) {
    throw new Error('백엔드 또는 데이터베이스가 정상 상태가 아닙니다.');
  }

  return { status: health.status };
}
