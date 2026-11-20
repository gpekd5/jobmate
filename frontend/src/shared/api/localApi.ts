export function isLoopbackHostname(hostname: string): boolean {
  return ['localhost', '127.0.0.1', '::1', '[::1]'].includes(
    hostname.toLowerCase(),
  );
}

export function canUsePersonalApi(): boolean {
  return (
    typeof location !== 'undefined' && isLoopbackHostname(location.hostname)
  );
}

const errorMessages: Record<string, string> = {
  RESUME_REQUIRED: '이력서를 먼저 등록하고 분석할 자료로 선택해 주세요.',
  PROFILE_CONFLICT:
    '다른 작업으로 프로필이 변경되었습니다. 프로필 화면을 새로 연 뒤 수정해 주세요.',
  INVALID_DOCUMENT:
    '지원하지 않거나 안전하게 읽을 수 없는 파일입니다. PDF·DOCX 내용과 암호 설정을 확인해 주세요.',
  DOCUMENT_TOO_LARGE: '파일은 10 MiB 이하여야 합니다.',
  PORTFOLIO_READ_FAILED:
    '선택한 공개 GitHub 프로젝트의 README를 읽을 수 없습니다.',
  ANALYSIS_ALREADY_RUNNING:
    '이미 문서 분석이 진행 중입니다. 상태를 먼저 확인해 주세요.',
  AI_BUSY:
    '다른 AI 요청을 처리 중입니다. 잠시 후 명시적으로 다시 시도해 주세요.',
  MATCH_DAILY_LIMIT:
    '오늘의 개별 공고 분석 3건을 모두 사용했습니다. 다음 날 다시 시도해 주세요.',
  AI_INPUT_TOO_LARGE:
    '선택한 자료의 내용이 분석 제한을 초과합니다. 자료를 나누거나 줄여 주세요.',
  AI_UNAVAILABLE:
    'OpenAI 연결에 실패했습니다. 자동으로 재분석하지 않습니다. 잠시 후 다시 시도해 주세요.',
  AI_CONSENT_REQUIRED:
    '이 분석 목적에 대한 OpenAI 전송 동의가 필요합니다. 프로필 설정에서 확인해 주세요.',
  PROFILE_NOT_CONFIRMED:
    '이력서 분석 결과를 검토하고 프로필을 먼저 확정해 주세요.',
  AI_NOT_CONFIGURED:
    '서버에 OpenAI 연결이 설정되지 않았습니다. 분석은 실행되지 않았습니다.',
  AI_PROVIDER_NOT_CONFIGURED:
    '서버에 OpenAI 연결이 설정되지 않았습니다. 분석은 실행되지 않았습니다.',
  AI_BUDGET_EXHAUSTED:
    'AI 분석 예산을 모두 사용했습니다. 저장된 결과와 전체 공고는 계속 확인할 수 있습니다.',
  AI_DAILY_LIMIT_EXCEEDED:
    '오늘의 AI 분석 한도에 도달했습니다. 다음 날 다시 시도해 주세요.',
  PROFILE_VERSION_CONFLICT:
    '다른 작업으로 프로필이 변경되었습니다. 새로 불러온 뒤 수정해 주세요.',
  LOCAL_ACCESS_ONLY:
    '개인정보 기능은 이 컴퓨터의 localhost에서만 사용할 수 있습니다.',
  DOCUMENT_NOT_FOUND: '문서를 찾을 수 없습니다. 삭제되었는지 확인해 주세요.',
};

export class PersonalApiError extends Error {
  code: string;
  status: number;
  constructor(code: string, status: number) {
    super(
      errorMessages[code] ??
        (status === 413
          ? '파일은 10 MiB 이하여야 합니다.'
          : status === 400 || status === 415
            ? '입력 형식 또는 지원 여부를 확인해 주세요.'
            : status === 401 || status === 403
              ? '접근이 제한되었습니다. 로컬 실행 여부와 동의를 확인해 주세요.'
              : status === 429
                ? '분석 횟수 또는 예산 한도에 도달했습니다. 잠시 후 다시 확인해 주세요.'
                : '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
    );
    this.name = 'PersonalApiError';
    this.code = code;
    this.status = status;
  }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export async function personalRequest<T>(
  path: string,
  signal: AbortSignal,
  validate: (value: unknown) => value is T,
  init: RequestInit = {},
): Promise<T> {
  if (!canUsePersonalApi())
    throw new PersonalApiError('LOCAL_ACCESS_ONLY', 403);
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
  });
  if (response.status === 204 && response.ok) {
    const empty: unknown = undefined;
    if (validate(empty)) return empty;
    throw new PersonalApiError('INVALID_RESPONSE', 204);
  }
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new PersonalApiError('INVALID_RESPONSE', response.status);
  }
  if (!response.ok) {
    const code =
      isRecord(body) &&
      isRecord(body.error) &&
      typeof body.error.code === 'string'
        ? body.error.code
        : 'REQUEST_FAILED';
    throw new PersonalApiError(code, response.status);
  }
  if (!isRecord(body) || body.success !== true || !validate(body.data))
    throw new PersonalApiError('INVALID_RESPONSE', response.status);
  return body.data;
}

export function jsonBody(value: unknown): RequestInit {
  return {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(value),
  };
}

export function personalError(error: unknown): string {
  if (error instanceof PersonalApiError) return error.message;
  if (error instanceof Error && error.name === 'TimeoutError')
    return '응답 시간이 초과되었습니다. 상태를 다시 확인해 주세요. 자동으로 분석을 재요청하지 않습니다.';
  return '서버에 연결할 수 없습니다. 연결을 확인하고 다시 시도해 주세요.';
}

export function abortableDelay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    signal.throwIfAborted();
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort);
      resolve();
    }, ms);
    function abort() {
      clearTimeout(timer);
      reject(signal.reason);
    }
    signal.addEventListener('abort', abort, { once: true });
  });
}
