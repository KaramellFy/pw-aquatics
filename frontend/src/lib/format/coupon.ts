import type { TFunction } from 'i18next';
import type { Money } from '@/lib/api/types';
import { formatMoney } from './money';
import { formatBps } from './number';

/** The fields of a coupon that say what it gives (cart, quote and admin views all have them). */
export interface CouponTerms {
  type: 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIPPING';
  percentage: number | null;
  amount: Money | null;
}

/** "10 % off", "€5.00 off" or "Free shipping". Formatting only: the values come from the API. */
export function couponBenefit(coupon: CouponTerms, t: TFunction, locale: string): string {
  switch (coupon.type) {
    case 'PERCENTAGE':
      return t('coupons.benefit.percentage', { value: formatBps(coupon.percentage ?? 0, locale) });
    case 'FIXED_AMOUNT':
      return t('coupons.benefit.amount', { value: coupon.amount ? formatMoney(coupon.amount, locale) : '' });
    case 'FREE_SHIPPING':
      return t('coupons.benefit.freeShipping');
  }
}
