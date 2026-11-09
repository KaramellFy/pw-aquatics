import { vi } from 'vitest';

type Handler = unknown | ((url: URL) => unknown);

/**
 * Stubs fetch for /api/v1 calls. Keys are paths (without /api/v1), matched exactly or by prefix
 * when they end with '*'. Returning a Response passes it through unchanged.
 */
export function mockApi(routes: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost');
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const key = Object.keys(routes).find((candidate) =>
      candidate.endsWith('*') ? path.startsWith(candidate.slice(0, -1)) : candidate === path,
    );
    if (!key) {
      return new Response(JSON.stringify({ status: 404, code: 'NOT_FOUND' }), {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    }
    const handler = routes[key];
    const body = typeof handler === 'function' ? (handler as (u: URL) => unknown)(url) : handler;
    if (body instanceof Response) return body;
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
