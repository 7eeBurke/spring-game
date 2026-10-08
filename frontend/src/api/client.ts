import type { ApiErrorBody } from './types';

/**
 * A failed API call. `kind` separates what the server said (`http`) from what we cannot know:
 * `network` and `timeout` mean the request may or may not have reached the server.
 * Never contains request headers, so it can never carry a token.
 */
export class ApiError extends Error {
  constructor(
    readonly kind: 'http' | 'network' | 'timeout',
    readonly status: number,
    readonly code: string,
    message: string,
    readonly reason?: string,
    readonly hint?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** The request may have been processed: retry only with the same idempotency key. */
  get uncertain(): boolean {
    return this.kind !== 'http' || this.status >= 500;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST';
  token?: string;
  headers?: Record<string, string>;
  body?: unknown;
  timeoutMs?: number;
}

export type Fetcher = typeof fetch;

let fetcher: Fetcher = (...args) => fetch(...args);

/** Tests replace the transport; production uses the browser's fetch. */
export function setFetcher(next: Fetcher): void {
  fetcher = next;
}

export function resetFetcher(): void {
  fetcher = (...args) => fetch(...args);
}

/** Calls the same-origin API. The token travels only in the Authorization header. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs ?? 30_000);
  let response: Response;
  try {
    response = await fetcher(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
    });
  } catch (cause) {
    const timedOut = controller.signal.aborted;
    throw new ApiError(timedOut ? 'timeout' : 'network', 0, timedOut ? 'TIMEOUT' : 'NETWORK',
      timedOut ? 'The server took too long to answer.' : 'Could not reach the server.');
  } finally {
    clearTimeout(timer);
  }

  const text = await response.text();
  let json: unknown = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  if (!response.ok) {
    const error = (json as ApiErrorBody | null)?.error;
    throw new ApiError('http', response.status, error?.code ?? `HTTP_${response.status}`,
      error?.message ?? 'The server could not complete the request.', error?.reason, error?.hint);
  }
  return json as T;
}
