import { describe, expect, it } from 'vitest';
import { loginPathFor, safeRedirectTarget } from './redirect';

describe('safeRedirectTarget', () => {
  it('accepts paths on this site', () => {
    expect(safeRedirectTarget('/account/addresses?x=1')).toBe('/account/addresses?x=1');
  });

  it.each([null, '', 'https://evil.example', '//evil.example', '/\\evil.example', 'account'])(
    'falls back to the account page for %s',
    (value) => {
      expect(safeRedirectTarget(value)).toBe('/account');
    },
  );

  it('builds a login link that returns to the current page', () => {
    expect(loginPathFor('/account/addresses', '?a=b')).toBe('/login?redirect=%2Faccount%2Faddresses%3Fa%3Db');
  });
});
