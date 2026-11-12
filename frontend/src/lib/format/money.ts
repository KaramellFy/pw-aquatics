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
 * Reads a price typed into an admin form ("3,90", "3.9", "12") as minor units (390).
 * Returns null for anything that is not a plain non-negative amount with at most two
 * decimals. This parses input; prices shown to customers always come from the server.
 */
export function parseMoneyInput(text: string, currency = 'EUR'): number | null {
  const normalised = text.trim().replace(',', '.');
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(normalised)) return null;
  return majorToMinorUnits(Number(normalised), currency);
}

/** Minor units as text for an input field: 390 → "3.90". Formatting only. */
export function moneyInputValue(cents: number, currency = 'EUR'): string {
  return toDecimalString({ amount: cents, currency });
}

/**
 * Converts a price typed by the user in major units (e.g. "3.5" €) to minor units for
 * filter queries. This parses user input; it never calculates a price.
 */
export function majorToMinorUnits(value: number, currency = 'EUR'): number {
  return Math.round(value * 10 ** fractionDigitsOf(currency));
}
