import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fetchBackendHealth } from '../src/features/backend-health/api.ts';

test('정상 응답이면 실제 health 경로를 요청하고 UP을 반환한다', async (t) => {
  // given
  const fetchMock = t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ status: 'UP' }),
  );

  // when
  const health = await fetchBackendHealth(new AbortController().signal);

  // then
  assert.deepEqual(health, { status: 'UP' });
  assert.equal(fetchMock.mock.calls[0].arguments[0], '/actuator/health');
  assert.ok(
    fetchMock.mock.calls[0].arguments[1]?.signal instanceof AbortSignal,
  );
});

test('HTTP 오류를 연결 성공으로 처리하지 않는다', async (t) => {
  // given
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ status: 'DOWN' }, { status: 503 }),
  );

  // when / then
  await assert.rejects(
    fetchBackendHealth(new AbortController().signal),
    /HTTP 503/,
  );
});

for (const body of [{ status: 'DOWN' }, {}, null, 'UP']) {
  test(`정상 health 계약이 아닌 응답 ${JSON.stringify(body)}를 거부한다`, async (t) => {
    // given
    t.mock.method(globalThis, 'fetch', async () => Response.json(body));

    // when / then
    await assert.rejects(
      fetchBackendHealth(new AbortController().signal),
      /정상 상태가 아닙니다/,
    );
  });
}

test('잘못된 JSON을 연결 성공으로 처리하지 않는다', async (t) => {
  // given
  t.mock.method(
    globalThis,
    'fetch',
    async () => new Response('<html>error</html>'),
  );

  // when / then
  await assert.rejects(
    fetchBackendHealth(new AbortController().signal),
    SyntaxError,
  );
});

test('네트워크 오류를 호출자에게 전달한다', async (t) => {
  // given
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('Failed to fetch');
  });

  // when / then
  await assert.rejects(
    fetchBackendHealth(new AbortController().signal),
    /Failed to fetch/,
  );
});

test('호출자가 취소하면 fetch 요청도 취소한다', async (t) => {
  // given
  const controller = new AbortController();
  controller.abort();
  t.mock.method(
    globalThis,
    'fetch',
    async (_input: RequestInfo | URL, init?: RequestInit) => {
      init?.signal?.throwIfAborted();
      return Response.json({ status: 'UP' });
    },
  );

  // when / then
  await assert.rejects(fetchBackendHealth(controller.signal), {
    name: 'AbortError',
  });
});
