/**
 * Number formatting for figures from the API. Presentation only: values are calculated by
 * the server; these helpers turn them into text.
 */

const cache = new Map<string, Intl.NumberFormat>();

function formatter(locale: string, options: Intl.NumberFormatOptions): Intl.NumberFormat {
  const key = `${locale}|${JSON.stringify(options)}`;
  let format = cache.get(key);
  if (!format) {
    format = new Intl.NumberFormat(locale, options);
    cache.set(key, format);
  }
  return format;
}

/** "1,284" / "1.284" */
export function formatCount(value: number, locale: string): string {
  return formatter(locale, { maximumFractionDigits: 0 }).format(value);
}

/** "12K" for axis ticks; plain digits below 10,000. */
export function formatCountCompact(value: number, locale: string): string {
  if (Math.abs(value) < 10_000) return formatCount(value, locale);
  return formatter(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

/**
 * A share or change given in basis points (1 % = 100) as a percentage: "12.5 %".
 * With `signed`, positive values get a plus sign ("+12.5 %").
 */
export function formatBps(bps: number, locale: string, { signed = false } = {}): string {
  return formatter(locale, {
    style: 'percent',
    maximumFractionDigits: 1,
    signDisplay: signed ? 'exceptZero' : 'auto',
  }).format(bps / 10_000);
}

/** Money in minor units as a compact axis label: "€1.2K", "€500". */
export function formatMinorUnitsCompact(minorUnits: number, currency: string, locale: string): string {
  const major = minorUnits / 100;
  return formatter(locale, {
    style: 'currency',
    currency,
    notation: Math.abs(major) >= 10_000 ? 'compact' : 'standard',
    maximumFractionDigits: Math.abs(major) >= 10_000 || Number.isInteger(major) ? 0 : 2,
  }).format(major);
}
