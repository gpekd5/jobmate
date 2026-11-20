import { useEffect, useState } from 'react';
import { canUsePersonalApi, isRecord, personalRequest } from './localApi';

interface Availability {
  available: boolean;
  reasonCode: string | null;
  model: string;
  budgetLimitUsd: number;
  reservedUsd: number;
}

export function AiAvailability() {
  const [status, setStatus] = useState<Availability | null>(null);
  useEffect(() => {
    if (!canUsePersonalApi()) return;
    const controller = new AbortController();
    personalRequest(
      '/api/v1/me/ai-status',
      controller.signal,
      (value): value is Availability =>
        isRecord(value) &&
        typeof value.available === 'boolean' &&
        typeof value.model === 'string',
    )
      .then((status) => {
        if (!controller.signal.aborted) setStatus(status);
      })
      .catch(() => {
        /* 설정 조회 실패는 서버의 분석 요청 검증으로 처리한다. */
      });
    return () => controller.abort();
  }, []);
  return status ? (
    <p
      className={`inline-notice ${status.available ? '' : 'error-notice'}`}
      role="status"
    >
      {status.available
        ? `OpenAI 분석 연결 준비됨 · 예산 한도 $${status.budgetLimitUsd}`
        : status.reasonCode === 'AI_BUDGET_EXHAUSTED'
          ? 'AI 분석 예산을 모두 사용했습니다. 저장된 결과는 계속 확인할 수 있습니다.'
          : 'OpenAI 연결 설정이 준비되지 않았습니다. 파일 저장과 프로필 직접 편집은 사용할 수 있습니다.'}
    </p>
  ) : null;
}
