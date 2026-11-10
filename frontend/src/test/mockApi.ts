import { vi } from 'vitest';

type Handler = unknown | ((url: URL, init: RequestInit | undefined) => unknown);

export function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, code, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/**
 * Stubs fetch for /api/v1 calls. Keys are paths (without /api/v1), optionally prefixed with a
 * method ("POST /auth/login"); a key ending in '*' matches by prefix. A handler may return a
 * Response (passed through), `undefined` (204) or any JSON value (200). Unknown paths give 404.
 * The CSRF endpoint is always answered.
 */
export function mockApi(routes: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const method = init?.method ?? 'GET';
    if (path === '/auth/csrf') {
      document.cookie = 'XSRF-TOKEN=test-token; path=/';
      return new Response(null, { status: 204 });
    }
    const matches = (key: string) => {
      const [keyMethod, keyPath] = key.includes(' ') ? (key.split(' ') as [string, string]) : ['GET', key];
      if (keyMethod !== method) return false;
      return keyPath.endsWith('*') ? path.startsWith(keyPath.slice(0, -1)) : keyPath === path;
    };
    const key = Object.keys(routes).find(matches);
    if (!key) return problem(404, 'NOT_FOUND');
    const handler = routes[key];
    const body =
      typeof handler === 'function'
        ? (handler as (u: URL, i: RequestInit | undefined) => unknown)(url, init)
        : handler;
    if (body instanceof Response) return body;
    if (body === undefined) return new Response(null, { status: 204 });
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** The JSON body of the last call to a path with the given method. */
export function lastBody(fetchMock: ReturnType<typeof mockApi>, method: string, path: string): unknown {
  const call = [...fetchMock.mock.calls]
    .reverse()
    .find(([input, init]) => String(input) === `/api/v1${path}` && (init?.method ?? 'GET') === method);
  if (!call) throw new Error(`No ${method} ${path} call was made`);
  return JSON.parse(String(call[1]?.body));
}
