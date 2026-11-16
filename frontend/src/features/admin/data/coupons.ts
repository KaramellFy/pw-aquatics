import type { TFunction } from 'i18next';
import { formatCalendarDate } from '@/lib/format/date';
import type { AdminCoupon } from '../api/coupons';

/** "1 Dec – 31 Dec", "From 1 Dec", "Until 31 Dec" or "Always". Formatting of the server's dates only. */
export function couponValidity(
  coupon: Pick<AdminCoupon, 'startsOn' | 'endsOn'>,
  t: TFunction,
  locale: string,
): string {
  const from = coupon.startsOn ? formatCalendarDate(coupon.startsOn, locale, 'medium') : null;
  const to = coupon.endsOn ? formatCalendarDate(coupon.endsOn, locale, 'medium') : null;
  if (from && to) return t('admin.coupons.validRange', { from, to });
  if (from) return t('admin.coupons.validFrom', { from });
  if (to) return t('admin.coupons.validUntil', { to });
  return t('admin.coupons.validAlways');
}
