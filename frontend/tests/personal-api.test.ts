import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { TestContext } from 'node:test';
import {
  abortableDelay,
  isLoopbackHostname,
  PersonalApiError,
  personalError,
} from '../src/shared/api/localApi.ts';
import {
  emptyPreferences,
  deleteDocument,
  emptyProfile,
  getDocuments,
  getPreferences,
  getProfile,
  saveConsent,
  savePreferences,
  saveProfile,
  startProfileAnalysis,
  uploadDocument,
  validateUpload,
  waitForProfileAnalysis,
} from '../src/features/profile/api.ts';
import {
  getMatch,
  getRecommendations,
  waitForMatch,
} from '../src/features/recommendations/api.ts';

function localHost(t: TestContext, hostname = 'localhost') {
  const previous = Object.getOwnPropertyDescriptor(globalThis, 'location');
  Object.defineProperty(globalThis, 'location', {
    configurable: true,
    value: { hostname },
  });
  t.after(() => {
    if (previous) Object.defineProperty(globalThis, 'location', previous);
    else Reflect.deleteProperty(globalThis, 'location');
  });
}
const profile = { ...emptyProfile, profileVersion: 0, confirmed: false };
const match = {
  evidenceCount: 0,
  evaluatedCategories: [],
  jobId: 1,
  status: 'NOT_ANALYZED',
  score: null,
  summary: null,
  strengths: [],
  gaps: [],
  unknowns: [],
  requirements: [],
  analyzedAt: null,
};

test('loopback hostname만 개인정보 기능에 허용한다', () => {
  // given / when / then
  for (const hostname of ['localhost', '127.0.0.1', '[::1]', '::1'])
    assert.equal(isLoopbackHostname(hostname), true);
  for (const hostname of [
    'example.com',
    'localhost.example.com',
    '192.168.0.1',
    '127.0.0.1.example.com',
  ])
    assert.equal(isLoopbackHostname(hostname), false);
});

test('공개 주소에서는 개인 API를 호출하기 전에 차단한다', async (t) => {
  // given
  localHost(t, 'example.com');
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ success: true, data: profile }),
  );
  // when / then
  await assert.rejects(
    getProfile(new AbortController().signal),
    (error: unknown) =>
      error instanceof PersonalApiError && error.code === 'LOCAL_ACCESS_ONLY',
  );
  assert.equal(fetchMock.mock.callCount(), 0);
});

test('업로드는 multipart 문서 저장 한 번만 요청하고 AI를 호출하지 않는다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: {
        documentId: 1,
        type: 'RESUME',
        fileName: 'example.pdf',
        analysisStatus: 'NOT_ANALYZED',
        uploadedAt: '2026-11-20T00:00:00Z',
      },
    }),
  );
  const file = new File(['self-written test content'], 'example.pdf', {
    type: 'application/pdf',
  });
  // when
  const document = await uploadDocument(
    file,
    'RESUME',
    new AbortController().signal,
  );
  // then
  assert.equal(document.analysisStatus, 'NOT_ANALYZED');
  assert.equal(fetchMock.mock.callCount(), 1);
  assert.equal(fetchMock.mock.calls[0].arguments[0], '/api/v1/me/documents');
  const init = fetchMock.mock.calls[0].arguments[1];
  assert.equal(init?.method, 'POST');
  assert.ok(init?.body instanceof FormData);
  assert.equal(init.body.get('type'), 'RESUME');
  assert.equal(init.headers, undefined);
});

test('빈 파일·10 MiB 초과·지원하지 않는 형식은 거부한다', () => {
  // given / when / then
  assert.match(validateUpload({ name: 'empty.pdf', size: 0 }) ?? '', /빈 파일/);
  assert.match(
    validateUpload({ name: 'large.pdf', size: 10 * 1024 * 1024 + 1 }) ?? '',
    /10 MiB/,
  );
  assert.match(
    validateUpload({ name: 'example.doc', size: 100 }) ?? '',
    /PDF 또는 DOCX/,
  );
  assert.equal(
    validateUpload({ name: 'example.DOCX', size: 10 * 1024 * 1024 }),
    null,
  );
});

test('목적별 동의 선택을 별도로 저장하고 분석을 시작하지 않는다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: {
        provider: 'OPENAI',
        purposes: ['PROFILE_EXTRACTION'],
        consented: true,
        consentVersion: 1,
      },
    }),
  );
  // when
  await saveConsent(['PROFILE_EXTRACTION'], new AbortController().signal);
  // then
  assert.equal(fetchMock.mock.callCount(), 1);
  assert.equal(fetchMock.mock.calls[0].arguments[0], '/api/v1/me/ai-consent');
  assert.deepEqual(
    JSON.parse(String(fetchMock.mock.calls[0].arguments[1]?.body)),
    { provider: 'OPENAI', purposes: ['PROFILE_EXTRACTION'], consented: true },
  );
});

test('명시적 분석 요청은 선택한 문서와 링크만 전송한다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      {
        success: true,
        data: { analysisId: 9, status: 'PENDING', result: null },
      },
      { status: 202 },
    ),
  );
  // when
  await startProfileAnalysis([1], [3], new AbortController().signal);
  // then
  assert.deepEqual(
    JSON.parse(String(fetchMock.mock.calls[0].arguments[1]?.body)),
    { documentIds: [1], portfolioLinkIds: [3], analysisMode: 'EXTERNAL_LLM' },
  );
});

test('분석 실패는 자동 재요청하지 않고 작업 상태를 반환한다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: {
        analysisId: 9,
        status: 'FAILED',
        result: null,
        errorCode: 'AI_UNAVAILABLE',
      },
    }),
  );
  // when
  const result = await waitForProfileAnalysis(9, new AbortController().signal);
  // then
  assert.equal(result.status, 'FAILED');
  assert.equal(fetchMock.mock.callCount(), 1);
  assert.equal(
    fetchMock.mock.calls[0].arguments[0],
    '/api/v1/me/profile/analyses/9',
  );
});

test('프로필 확정은 검토한 내용과 기존 버전을 전달한다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: { ...profile, confirmed: true, profileVersion: 1 },
    }),
  );
  // when
  await saveProfile(profile, new AbortController().signal);
  // then
  const body = JSON.parse(String(fetchMock.mock.calls[0].arguments[1]?.body));
  assert.equal(body.profileVersion, 0);
  assert.equal(body.confirmed, true);
});

test('빈 선택 선호 조건을 빈 문자열 배열로 보내지 않는다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ success: true, data: emptyPreferences }),
  );
  // when
  await savePreferences(
    { ...emptyPreferences, desiredRoles: [''], regions: [''] },
    new AbortController().signal,
  );
  // then
  assert.deepEqual(
    JSON.parse(String(fetchMock.mock.calls[0].arguments[1]?.body)).regions,
    [],
  );
});

test('직무·지역만 바꿔도 다른 희망 조건은 보존되고 다시 조회된다', async (t) => {
  // given
  localHost(t);
  let stored = {
    desiredRoles: ['BACKEND'],
    regions: ['서울'],
    employmentTypes: ['FULL_TIME', 'CONTRACT'],
    minSalary: 40000000,
    avoidDispatch: true,
    avoidFrequentTravel: true,
  };
  t.mock.method(
    globalThis,
    'fetch',
    async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'PUT') stored = JSON.parse(String(init.body));
      return Response.json({ success: true, data: stored });
    },
  );
  // when
  const current = await getPreferences(new AbortController().signal);
  await savePreferences(
    { ...current, desiredRoles: ['FRONTEND'], regions: ['경기'] },
    new AbortController().signal,
  );
  const reloaded = await getPreferences(new AbortController().signal);
  // then
  assert.deepEqual(reloaded, {
    desiredRoles: ['FRONTEND'],
    regions: ['경기'],
    employmentTypes: ['FULL_TIME', 'CONTRACT'],
    minSalary: 40000000,
    avoidDispatch: true,
    avoidFrequentTravel: true,
  });
});

test('고용 형태·연봉·파견·출장 조건을 저장하고 미선택 값도 다시 조회한다', async (t) => {
  // given
  localHost(t);
  let stored = {
    ...emptyPreferences,
    employmentTypes: ['FULL_TIME'],
    minSalary: 50000000 as number | null,
    avoidDispatch: true,
    avoidFrequentTravel: true,
  };
  t.mock.method(
    globalThis,
    'fetch',
    async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'PUT') stored = JSON.parse(String(init.body));
      return Response.json({ success: true, data: stored });
    },
  );
  // when
  await savePreferences(
    {
      ...stored,
      employmentTypes: [],
      minSalary: null,
      avoidDispatch: false,
      avoidFrequentTravel: false,
    },
    new AbortController().signal,
  );
  const reloaded = await getPreferences(new AbortController().signal);
  // then
  assert.deepEqual(reloaded, emptyPreferences);
});

for (const code of [
  'AI_CONSENT_REQUIRED',
  'PROFILE_NOT_CONFIRMED',
  'AI_NOT_CONFIGURED',
  'AI_BUDGET_EXHAUSTED',
  'MATCH_DAILY_LIMIT',
]) {
  test(`API 오류 ${code}는 원문 오류 대신 구분된 안내를 제공한다`, async (t) => {
    // given
    localHost(t);
    t.mock.method(globalThis, 'fetch', async () =>
      Response.json(
        {
          success: false,
          error: { code, message: 'private raw data must not render' },
        },
        { status: 403 },
      ),
    );
    // when / then
    await assert.rejects(
      getMatch(1, new AbortController().signal),
      (error: unknown) =>
        error instanceof PersonalApiError &&
        error.code === code &&
        !personalError(error).includes('private raw'),
    );
  });
}

test('미분석 추천은 null 점수를 보존하고 읽음 상태를 요청하지 않는다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: {
        items: [
          {
            jobId: 1,
            title: '자체 예시',
            companyName: '테스트 기업',
            source: 'MANUAL',
            region: null,
            originalUrl: null,
            match,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      },
    }),
  );
  // when
  const result = await getRecommendations(
    0,
    'MATCH_SCORE',
    'ALL',
    new AbortController().signal,
  );
  // then
  assert.equal(result.items[0].match.score, null);
  assert.ok(
    !String(fetchMock.mock.calls[0].arguments[0]).includes('viewStatus'),
  );
});

test('100을 넘는 점수는 정상 AI 결과로 처리하지 않는다', async (t) => {
  // given
  localHost(t);
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: { ...match, status: 'READY', score: 101 },
    }),
  );
  // when / then
  await assert.rejects(
    getMatch(1, new AbortController().signal),
    (error: unknown) =>
      error instanceof PersonalApiError && error.code === 'INVALID_RESPONSE',
  );
});

test('종료된 매칭 상태는 재분석 없이 반환한다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: { ...match, status: 'INSUFFICIENT_DATA' },
    }),
  );
  // when
  const result = await waitForMatch(1, new AbortController().signal);
  // then
  assert.equal(result.status, 'INSUFFICIENT_DATA');
  assert.equal(fetchMock.mock.callCount(), 1);
});

test('화면 이동 취소는 폴링 대기 시간을 즉시 종료한다', async () => {
  // given
  const controller = new AbortController();
  const pending = abortableDelay(30000, controller.signal);
  // when
  controller.abort();
  // then
  await assert.rejects(pending, { name: 'AbortError' });
});

test('잘못된 문서 응답은 빈 목록으로 숨기지 않는다', async (t) => {
  // given
  localHost(t);
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ success: true, data: {} }),
  );
  // when / then
  await assert.rejects(
    getDocuments(new AbortController().signal),
    (error: unknown) =>
      error instanceof PersonalApiError && error.code === 'INVALID_RESPONSE',
  );
});

test('삭제는 204 무본문 응답을 처리한다', async (t) => {
  // given
  localHost(t);
  const fetchMock = t.mock.method(
    globalThis,
    'fetch',
    async () => new Response(null, { status: 204 }),
  );
  // when
  await deleteDocument(1, new AbortController().signal);
  // then
  assert.equal(fetchMock.mock.calls[0].arguments[1]?.method, 'DELETE');
});

test('조회가 잘못 반환한 204를 정상 프로필로 처리하지 않는다', async (t) => {
  // given
  localHost(t);
  t.mock.method(
    globalThis,
    'fetch',
    async () => new Response(null, { status: 204 }),
  );
  // when / then
  await assert.rejects(
    getProfile(new AbortController().signal),
    (error: unknown) =>
      error instanceof PersonalApiError && error.code === 'INVALID_RESPONSE',
  );
});
