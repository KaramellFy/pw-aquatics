import { describe, expect, it } from 'vitest';
import { formatMoney } from './money';

describe('formatMoney', () => {
  it('formats cents as euros in German', () => {
    expect(formatMoney({ amount: 1299, currency: 'EUR' }, 'de-DE')).toBe('12,99 €');
  });

  it('formats cents as euros in English', () => {
    expect(formatMoney({ amount: 390, currency: 'EUR' }, 'en-GB')).toBe('€3.90');
  });

  it('respects currencies without minor units', () => {
    expect(formatMoney({ amount: 500, currency: 'JPY' }, 'en-GB')).toBe('JP¥500');
  });
});

describe('money conversions', () => {
  it('formats a decimal string for structured data', async () => {
    const { toDecimalString } = await import('./money');
    expect(toDecimalString({ amount: 390, currency: 'EUR' })).toBe('3.90');
  });

  it('parses a user-entered euro amount into cents', async () => {
    const { majorToMinorUnits } = await import('./money');
    expect(majorToMinorUnits(3.5)).toBe(350);
    expect(majorToMinorUnits(0.1 + 0.2)).toBe(30);
  });
});
