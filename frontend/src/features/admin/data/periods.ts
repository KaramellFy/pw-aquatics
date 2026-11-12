/**
 * Reporting periods offered on the dashboard. A period is a pair of calendar dates in the
 * shop's time zone (both inclusive); the server works out buckets, comparisons and figures.
 */

export interface Period {
  from: string;
  to: string;
}

export const periodPresets = ['7d', '30d', '90d', '12m', 'ytd'] as const;
export type PeriodPreset = (typeof periodPresets)[number];

/** The default when the address names no period. */
export const DEFAULT_PRESET: PeriodPreset = '30d';

const SHOP_ZONE = 'Europe/Berlin';
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Today's date ("2026-11-12") on the shop's calendar. */
export function todayInShop(now: Date = new Date()): string {
  // en-CA formats dates as YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', { timeZone: SHOP_ZONE }).format(now);
}

/** Moves a calendar date by whole days (calendar arithmetic only, no time zones involved). */
export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** The dates a preset stands for, ending today. */
export function presetPeriod(preset: PeriodPreset, today: string): Period {
  switch (preset) {
    case '7d':
      return { from: addDays(today, -6), to: today };
    case '30d':
      return { from: addDays(today, -29), to: today };
    case '90d':
      return { from: addDays(today, -89), to: today };
    case '12m': {
      const yearAgo = new Date(`${today}T00:00:00Z`);
      yearAgo.setUTCFullYear(yearAgo.getUTCFullYear() - 1);
      return { from: addDays(yearAgo.toISOString().slice(0, 10), 1), to: today };
    }
    case 'ytd':
      return { from: `${today.slice(0, 4)}-01-01`, to: today };
  }
}

export function isPreset(value: string | null): value is PeriodPreset {
  return value !== null && (periodPresets as readonly string[]).includes(value);
}

/** True for a well-formed "YYYY-MM-DD" date. */
export function isIsoDate(value: string | null): value is string {
  return value !== null && ISO_DATE.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`));
}

/**
 * Reads the period from the address: `?period=90d`, or `?from=…&to=…` for a custom one.
 * Anything unreadable falls back to the default preset.
 */
export function periodFromParams(
  params: URLSearchParams,
  today: string,
): { period: Period; preset: PeriodPreset | null } {
  const from = params.get('from');
  const to = params.get('to');
  if (isIsoDate(from) && isIsoDate(to)) {
    return { period: { from, to }, preset: null };
  }
  const preset = params.get('period');
  const chosen = isPreset(preset) ? preset : DEFAULT_PRESET;
  return { period: presetPeriod(chosen, today), preset: chosen };
}
