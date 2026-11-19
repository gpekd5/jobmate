import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  buildJobQuery,
  fetchJob,
  fetchJobs,
  JobsApiError,
  readFilters,
  safeOriginalUrl,
} from '../src/features/jobs/api.ts';

const summary = {
  jobId: 1,
  source: 'MANUAL',
  companyName: '자체 예시 기업',
  title: '개발자',
  region: null,
  employmentType: null,
  experienceText: null,
  postedAt: null,
  deadlineAt: null,
  active: true,
  originalUrl: null,
  detailAvailability: 'UNAVAILABLE',
  lastCollectedAt: null,
  jobVersion: 1,
  updatedAt: '2026-11-20T09:00:00+09:00',
};
const detail = {
  ...summary,
  externalJobId: null,
  jobRole: null,
  educationText: null,
  salaryText: null,
  responsibilities: null,
  requiredQualifications: [],
  preferredQualifications: ['직접 작성한 테스트 우대사항'],
  skills: null,
  workConditions: null,
  detailSource: 'UNKNOWN',
  detailExtractedAt: null,
};

test('검색 조건은 URL 인코딩되고 목록 복귀 시 보존된다', () => {
  // given
  const filters = {
    ...readFilters(''),
    keyword: ' Java & Spring ',
    region: '서울',
    jobRole: 'BACKEND',
    activeOnly: false,
    page: 2,
    size: 50,
  };
  // when
  const query = buildJobQuery(filters);
  // then
  assert.deepEqual(readFilters(`?${query}`), {
    ...filters,
    keyword: 'Java & Spring',
  });
  assert.equal(new URLSearchParams(query).get('keyword'), 'Java & Spring');
});

test('잘못된 페이지·크기·정렬·출처는 안전한 기본 조건을 사용한다', () => {
  // given / when
  const filters = readFilters(
    '?page=-1&size=999&sort=MATCH_SCORE&source=UNKNOWN',
  );
  // then
  assert.equal(filters.page, 0);
  assert.equal(filters.size, 20);
  assert.equal(filters.sort, 'NEWEST');
  assert.equal(filters.source, '');
  assert.equal(filters.activeOnly, true);
});

test('전체 공고는 실제 jobs API의 data envelope를 읽는다', async (t) => {
  // given
  const page = {
    items: [summary],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
  };
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ success: true, data: page }),
  );
  // when
  const result = await fetchJobs(readFilters(''), new AbortController().signal);
  // then
  assert.deepEqual(result, page);
  assert.equal(
    fetchMock.mock.calls[0].arguments[0],
    '/api/v1/jobs?activeOnly=true&sort=NEWEST&page=0&size=20',
  );
});

test('상세는 미확인·해당없음·실제내용을 서로 보존한다', async (t) => {
  // given
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ success: true, data: detail }),
  );
  // when
  const result = await fetchJob(1, new AbortController().signal);
  // then
  assert.equal(fetchMock.mock.calls[0].arguments[0], '/api/v1/jobs/1');
  assert.equal(result.responsibilities, null);
  assert.deepEqual(result.requiredQualifications, []);
  assert.deepEqual(result.preferredQualifications, [
    '직접 작성한 테스트 우대사항',
  ]);
});

for (const [index, body] of [
  { success: false, data: detail },
  {
    success: true,
    data: { ...detail, responsibilities: '없는 필드를 추측하지 않는다' },
  },
  { success: true, data: { ...detail, source: 'UNKNOWN' } },
  { success: true, data: null },
].entries()) {
  test(`상세 응답 계약 위반을 거부한다 (${index + 1})`, async (t) => {
    // given
    t.mock.method(globalThis, 'fetch', async () => Response.json(body));
    // when / then
    await assert.rejects(
      fetchJob(1, new AbortController().signal),
      /응답 형식/,
    );
  });
}

test('404는 빈 결과나 성공 대신 공고 부재 오류를 전달한다', async (t) => {
  // given
  t.mock.method(
    globalThis,
    'fetch',
    async () => new Response(null, { status: 404 }),
  );
  // when / then
  await assert.rejects(
    fetchJob(99, new AbortController().signal),
    (error: unknown) => error instanceof JobsApiError && error.status === 404,
  );
});

test('잘못된 목록 응답을 빈 검색 결과로 처리하지 않는다', async (t) => {
  // given
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: {
        items: null,
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      },
    }),
  );
  // when / then
  await assert.rejects(
    fetchJobs(readFilters(''), new AbortController().signal),
    /응답 형식/,
  );
});

test('출처·마감 임박 정렬·마감 포함 조건을 API에 전달한다', async (t) => {
  // given
  const filters = readFilters(
    '?source=WANTED&sort=DEADLINE&activeOnly=false&page=2&size=50',
  );
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      success: true,
      data: {
        items: [],
        page: 2,
        size: 50,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      },
    }),
  );
  // when
  await fetchJobs(filters, new AbortController().signal);
  // then
  assert.equal(
    fetchMock.mock.calls[0].arguments[0],
    '/api/v1/jobs?source=WANTED&activeOnly=false&sort=DEADLINE&page=2&size=50',
  );
});

test('화면 이동으로 취소한 공고 요청을 취소 신호로 전달한다', async (t) => {
  // given
  const controller = new AbortController();
  controller.abort();
  t.mock.method(
    globalThis,
    'fetch',
    async (_input: RequestInfo | URL, init?: RequestInit) => {
      init?.signal?.throwIfAborted();
      return Response.json({ success: true, data: detail });
    },
  );
  // when / then
  await assert.rejects(fetchJob(1, controller.signal), { name: 'AbortError' });
});

test('네트워크 장애는 빈 목록으로 숨기지 않는다', async (t) => {
  // given
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('Failed to fetch');
  });
  // when / then
  await assert.rejects(
    fetchJobs(readFilters(''), new AbortController().signal),
    TypeError,
  );
});

test('원문은 http(s) 링크만 허용하고 스크립트·상대경로·자격증명은 거부한다', () => {
  // given / when / then
  for (const url of [
    'javascript:alert(1)',
    'data:text/html,test',
    '/relative',
    'https://user:password@example.com',
    '',
    null,
  ]) {
    assert.equal(safeOriginalUrl(url), null);
  }
  assert.equal(
    safeOriginalUrl('https://example.com/jobs/1'),
    'https://example.com/jobs/1',
  );
  assert.equal(
    safeOriginalUrl('http://example.com/jobs/1'),
    'http://example.com/jobs/1',
  );
});
