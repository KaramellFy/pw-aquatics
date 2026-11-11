import i18n from '@/lib/i18n';
import type { FieldError, ProblemDetails } from './types';

/**
 * The single HTTP entry point of the frontend.
 *
 * - Always same-origin (`/api/v1/...`) with cookies included (session auth).
 * - Adds the CSRF header to every state-changing request (cookie-to-header pattern).
 * - Turns RFC 9457 Problem Details into a typed `ApiError`.
 */

export const API_BASE = '/api/v1';

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

/** Error thrown for every failed API call. `code` is the stable machine-readable error code. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  readonly traceId?: string;

  constructor(problem: ProblemDetails) {
    super(problem.detail ?? problem.title ?? 'Request failed');
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code ?? codeForStatus(problem.status);
    this.fieldErrors = problem.errors ?? [];
    this.traceId = problem.traceId;
  }
}

function codeForStatus(status: number): string {
  if (status === 0) return 'NETWORK_ERROR';
  if (status === 401) return 'UNAUTHENTICATED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMITED';
  if (status >= 500) return 'SERVICE_UNAVAILABLE';
  return 'REQUEST_FAILED';
}

export function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const entry = document.cookie.split('; ').find((part) => part.startsWith(prefix));
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}

/** Makes sure the CSRF cookie exists; the backend sets it on `GET /auth/csrf`. */
async function ensureCsrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  await fetch(`${API_BASE}/auth/csrf`, { credentials: 'include' });
  return readCookie(CSRF_COOKIE);
}

async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('json')) {
    try {
      const body = (await response.json()) as Partial<ProblemDetails>;
      return new ApiError({ ...body, status: body.status ?? response.status });
    } catch {
      // fall through to a generic error
    }
  }
  return new ApiError({ status: response.status, title: response.statusText });
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {
    Accept: 'application/json',
    // Server-side validation messages follow the UI language.
    'Accept-Language': i18n.resolvedLanguage ?? i18n.language,
    ...options.headers,
  };

  // FormData (file uploads) sets its own multipart content type with the boundary.
  const isForm = options.body instanceof FormData;
  if (options.body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json';
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) headers[CSRF_HEADER] = token;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: isForm
        ? (options.body as FormData)
        : options.body !== undefined
          ? JSON.stringify(options.body)
          : undefined,
      credentials: 'include',
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError({ status: 0, title: 'Network error' });
  }

  if (!response.ok) throw await toApiError(response);
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T;
  }
  const contentType = response.headers.get('content-type') ?? '';
  return (contentType.includes('json') ? await response.json() : undefined) as T;
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => apiRequest<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'DELETE', body }),
};
