import { useTranslation } from 'react-i18next';
import type { CouponStatus } from '../api/coupons';
import { Pill } from './StatusBadge';

/** Where a coupon stands right now, as decided by the server. */
export function CouponStatusPill({ status }: { status: CouponStatus }) {
  const { t } = useTranslation();
  const tone =
    status === 'ACTIVE' ? 'ok' : status === 'USED_UP' || status === 'EXPIRED' ? 'warning' : 'neutral';
  return <Pill tone={tone}>{t(`admin.coupons.status.${status}`)}</Pill>;
}
