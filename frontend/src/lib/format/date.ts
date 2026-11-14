import { shopConfig } from '@/config/shop';

type DateStyle = 'full' | 'long' | 'medium';

/**
 * Formats a calendar date from the API ("2026-11-16", no time, no zone). It is formatted
 * in UTC so it can never slip to the previous or next day in the visitor's time zone.
 */
export function formatCalendarDate(isoDate: string, locale: string, dateStyle: DateStyle = 'long'): string {
  return new Intl.DateTimeFormat(locale, { dateStyle, timeZone: 'UTC' }).format(new Date(isoDate));
}

/**
 * Formats the day of a point in time from the API (e.g. when an order was placed or by
 * when it must be paid) as it reads on the shop's calendar.
 */
export function formatInstantDate(instant: string, locale: string, dateStyle: DateStyle = 'long'): string {
  return new Intl.DateTimeFormat(locale, { dateStyle, timeZone: shopConfig.timeZone }).format(
    new Date(instant),
  );
}

/** Day and time of a point in time, in the shop's time zone (status timeline). */
export function formatInstantDateTime(instant: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: shopConfig.timeZone,
  }).format(new Date(instant));
}

/** A calendar date with custom parts ("12 Nov", "November 2026"), formatted in UTC like above. */
export function formatCalendarDateWith(
  isoDate: string,
  locale: string,
  options: Omit<Intl.DateTimeFormatOptions, 'timeZone'>,
): string {
  return new Intl.DateTimeFormat(locale, { ...options, timeZone: 'UTC' }).format(new Date(isoDate));
}

/**
 * The time of a point in the next few hours (the deadline of a card payment), in the
 * visitor's own time zone: "11:30" means 11:30 on the clock in front of them.
 */
export function formatLocalTime(instant: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { timeStyle: 'short' }).format(new Date(instant));
}
