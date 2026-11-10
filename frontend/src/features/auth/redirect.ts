const DEFAULT_TARGET = '/account';

/**
 * Where to go after signing in. Only same-site paths are accepted, so a crafted
 * link like /login?redirect=https://evil.example cannot send users elsewhere.
 */
export function safeRedirectTarget(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return DEFAULT_TARGET;
  }
  return value;
}

export function loginPathFor(pathname: string, search = ''): string {
  return `/login?redirect=${encodeURIComponent(pathname + search)}`;
}
