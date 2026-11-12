import { describe, expect, it } from 'vitest';
import { formatMoney, moneyInputValue, parseMoneyInput } from './money';

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

describe('parseMoneyInput', () => {
  it('reads German and English decimal notation as cents', () => {
    expect(parseMoneyInput('3,90')).toBe(390);
    expect(parseMoneyInput(' 3.9 ')).toBe(390);
    expect(parseMoneyInput('12')).toBe(1200);
    expect(parseMoneyInput('0,07')).toBe(7);
    // Floating point must not lose a cent.
    expect(parseMoneyInput('19.99')).toBe(1999);
    expect(parseMoneyInput('1.005')).toBeNull();
  });

  it('refuses anything that is not a plain amount', () => {
    for (const text of ['', '-1', '1.234,50', '€3', 'abc', '3,999', '12345678']) {
      expect(parseMoneyInput(text)).toBeNull();
    }
  });

  it('turns cents back into input text', () => {
    expect(moneyInputValue(390)).toBe('3.90');
    expect(moneyInputValue(1200)).toBe('12.00');
  });
});
