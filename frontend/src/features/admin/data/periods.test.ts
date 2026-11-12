import { describe, expect, it } from 'vitest';
import { addDays, periodFromParams, presetPeriod, todayInShop } from './periods';

describe('dashboard periods', () => {
  it('uses the date in Berlin, not the visitor’s', () => {
    // 23:30 UTC on 31 December is already New Year in Berlin
    expect(todayInShop(new Date('2026-12-31T23:30:00Z'))).toBe('2027-01-01');
    expect(todayInShop(new Date('2026-07-01T21:59:00Z'))).toBe('2026-07-01');
  });

  it('moves dates across months and leap days', () => {
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('turns presets into dates ending today', () => {
    expect(presetPeriod('7d', '2026-11-12')).toEqual({ from: '2026-11-06', to: '2026-11-12' });
    expect(presetPeriod('30d', '2026-11-12')).toEqual({ from: '2026-10-14', to: '2026-11-12' });
    expect(presetPeriod('90d', '2026-11-12')).toEqual({ from: '2026-08-15', to: '2026-11-12' });
    expect(presetPeriod('12m', '2026-11-12')).toEqual({ from: '2025-11-13', to: '2026-11-12' });
    expect(presetPeriod('ytd', '2026-11-12')).toEqual({ from: '2026-01-01', to: '2026-11-12' });
  });

  it('reads the period from the address', () => {
    const read = (query: string) => periodFromParams(new URLSearchParams(query), '2026-11-12');
    expect(read('')).toEqual({ period: { from: '2026-10-14', to: '2026-11-12' }, preset: '30d' });
    expect(read('period=7d').preset).toBe('7d');
    expect(read('period=nonsense').preset).toBe('30d');
    expect(read('from=2026-01-01&to=2026-01-31')).toEqual({
      period: { from: '2026-01-01', to: '2026-01-31' },
      preset: null,
    });
    expect(read('from=2026-13-45&to=2026-01-31').preset).toBe('30d');
  });
});
