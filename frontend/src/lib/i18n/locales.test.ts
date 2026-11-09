import { describe, expect, it } from 'vitest';
import { de } from './locales/de';
import { en } from './locales/en';

function keysOf(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return [prefix];
  return Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix ? `${prefix}.${key}` : key));
}

describe('locales', () => {
  it('English provides exactly the German keys', () => {
    expect(keysOf(en).sort()).toEqual(keysOf(de).sort());
  });
});
