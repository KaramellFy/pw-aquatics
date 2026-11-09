import type { Money } from '@/lib/api/types';

const formatters = new Map<string, Intl.NumberFormat>();

function formatterFor(locale: string, currency: string): Intl.NumberFormat {
  const key = `${locale}|${currency}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { style: 'currency', currency });
    formatters.set(key, formatter);
  }
  return formatter;
}

/**
 * Formats a server-provided Money value (minor units) for display.
 * This is presentation only: it converts cents to the currency's major unit
 * for Intl, it never calculates prices.
 */
export function formatMoney(money: Money, locale: string): string {
  const formatter = formatterFor(locale, money.currency);
  const fractionDigits = formatter.resolvedOptions().maximumFractionDigits ?? 2;
  return formatter.format(money.amount / 10 ** fractionDigits);
}

function fractionDigitsOf(currency: string): number {
  return formatterFor('en', currency).resolvedOptions().maximumFractionDigits ?? 2;
}

/** "3.90" — plain decimal string for structured data (JSON-LD). Formatting only. */
export function toDecimalString(money: Money): string {
  const digits = fractionDigitsOf(money.currency);
  return (money.amount / 10 ** digits).toFixed(digits);
}

/**
 * Converts a price typed by the user in major units (e.g. "3.5" €) to minor units for
 * filter queries. This parses user input; it never calculates a price.
 */
export function majorToMinorUnits(value: number, currency = 'EUR'): number {
  return Math.round(value * 10 ** fractionDigitsOf(currency));
}
