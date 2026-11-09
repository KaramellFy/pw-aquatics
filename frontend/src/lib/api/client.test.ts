import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './client';

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api client', () => {
  it('sends GET requests same-origin with credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.get('/products')).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/products',
      expect.objectContaining({ method: 'GET', credentials: 'include' }),
    );
  });

  it('attaches the CSRF token header to state-changing requests', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }));
    vi.stubGlobal('fetch', fetchMock);

    await api.post('/contact', { name: 'Anna' });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'abc123', 'Content-Type': 'application/json' });
    expect(init.body).toBe(JSON.stringify({ name: 'Anna' }));
  });

  it('fetches a CSRF token first when the cookie is missing', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(async () => {
        document.cookie = 'XSRF-TOKEN=fresh; path=/';
        return new Response(null, { status: 204 });
      })
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await api.delete('/cart');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/csrf');
    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'fresh' });
  });

  it('maps Problem Details to ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            status: 400,
            code: 'VALIDATION_FAILED',
            detail: 'Invalid input',
            errors: [{ field: 'email', code: 'INVALID', message: 'Invalid email' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await api.get('/x').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', message: 'Invalid input' });
    expect((error as ApiError).fieldErrors).toHaveLength(1);
  });

  it('reports unreachable backends as NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api.get('/x')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it('falls back to a status-based code for non-JSON errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Bad gateway', { status: 502 })));
    await expect(api.get('/x')).rejects.toMatchObject({ status: 502, code: 'SERVICE_UNAVAILABLE' });
  });
});
