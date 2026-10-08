import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, json, networkFailure } from '../test/fakeApi';
import { ApiError, apiRequest, setFetcher } from './client';

describe('the API client', () => {
  afterEach(() => vi.useRealTimers());

  it('sends the token only in the Authorization header', async () => {
    const api = fakeApi(() => json(200, { ok: true }));
    await apiRequest('/api/v1/runs/x', { token: 'T'.repeat(43) });
    expect(api.calls[0]!.headers.Authorization).toBe(`Bearer ${'T'.repeat(43)}`);
    expect(api.calls[0]!.url).not.toContain('T'.repeat(43));
  });

  it('maps an API error body to a typed error', async () => {
    fakeApi(() => json(409, { error: { code: 'STALE_VIEW', message: 'moved on', hint: 'reload' } }));
    const error = await apiRequest('/api/x').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ kind: 'http', status: 409, code: 'STALE_VIEW', hint: 'reload' });
    expect((error as ApiError).uncertain).toBe(false);
  });

  it('treats a lost connection and server errors as uncertain', async () => {
    fakeApi(() => networkFailure());
    const lost = (await apiRequest('/api/x').catch((e: unknown) => e)) as ApiError;
    expect(lost.kind).toBe('network');
    expect(lost.uncertain).toBe(true);

    fakeApi(() => new Response('<html>oops</html>', { status: 502 }));
    const bad = (await apiRequest('/api/x').catch((e: unknown) => e)) as ApiError;
    expect(bad).toMatchObject({ kind: 'http', status: 502, code: 'HTTP_502' });
    expect(bad.uncertain).toBe(true);
  });

  it('gives up after its timeout without knowing the outcome', async () => {
    vi.useFakeTimers();
    setFetcher(((_: RequestInfo | URL, init?: RequestInit) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    })) as typeof fetch);
    const pending = apiRequest('/api/x', { timeoutMs: 1000 }).catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(1001);
    const error = (await pending) as ApiError;
    expect(error.kind).toBe('timeout');
    expect(error.uncertain).toBe(true);
  });
});
