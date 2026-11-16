import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { couponBenefit } from '@/lib/format/coupon';
import { formatMoney } from '@/lib/format/money';
import { formatCount } from '@/lib/format/number';
import { useIntlLocale } from '@/lib/i18n';
import { useAdminCoupons, type CouponStatus } from '../api/coupons';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { CouponDialog } from '../components/CouponDialog';
import { CouponStatusPill } from '../components/CouponStatusPill';
import { DataTable, Td, Th } from '../components/DataTable';
import { FilterTabs } from '../components/FilterTabs';
import { LoadState } from '../components/LoadState';
import { couponValidity } from '../data/coupons';

type StatusFilter = 'ALL' | CouponStatus;
const FILTERS: StatusFilter[] = ['ALL', 'ACTIVE', 'SCHEDULED', 'EXPIRED', 'USED_UP', 'INACTIVE'];

/** Coupons with their current status (decided by the server) and how often each was used. */
export function AdminCouponsPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const statusParam = params.get('status') as StatusFilter | null;
  const status: StatusFilter = statusParam && FILTERS.includes(statusParam) ? statusParam : 'ALL';
  const coupons = useAdminCoupons();
  const [creating, setCreating] = useState(false);
  const [dialogKey, setDialogKey] = useState(0);
  const shown = coupons.data?.filter((coupon) => status === 'ALL' || coupon.status === status) ?? [];

  const setStatus = (next: StatusFilter) => {
    const updated = new URLSearchParams(params);
    if (next === 'ALL') updated.delete('status');
    else updated.set('status', next);
    setParams(updated, { replace: true });
  };

  return (
    <>
      <AdminPageHeader
        title={t('admin.coupons.title')}
        lead={t('admin.coupons.lead')}
        actions={
          <Button
            onClick={() => {
              setDialogKey((key) => key + 1);
              setCreating(true);
            }}
          >
            <Plus aria-hidden className="size-4" />
            {t('admin.coupons.add')}
          </Button>
        }
      />
      <FilterTabs
        id="coupon-status"
        label={t('admin.coupons.filterLabel')}
        className="mb-6"
        options={FILTERS.map((value) => ({
          value,
          label: value === 'ALL' ? t('admin.coupons.all') : t(`admin.coupons.status.${value}`),
          count: coupons.data
            ? coupons.data.filter((coupon) => value === 'ALL' || coupon.status === value).length
            : undefined,
        }))}
        value={status}
        onChange={setStatus}
      />

      <LoadState
        isPending={coupons.isPending}
        isError={coupons.isError}
        hasData={Boolean(coupons.data)}
        onRetry={() => void coupons.refetch()}
      />
      {coupons.data && shown.length === 0 && (
        <p className="rounded-card border border-line bg-surface p-8 text-center text-ink-soft">
          {coupons.data.length === 0 ? t('admin.coupons.empty') : t('admin.coupons.noneWithStatus')}
        </p>
      )}
      {shown.length > 0 && (
        <DataTable
          caption={t('admin.coupons.title')}
          head={
            <tr>
              <Th>{t('admin.coupons.columns.code')}</Th>
              <Th>{t('admin.coupons.columns.benefit')}</Th>
              <Th>{t('admin.coupons.columns.valid')}</Th>
              <Th className="text-right">{t('admin.coupons.columns.used')}</Th>
              <Th>{t('admin.coupons.columns.status')}</Th>
            </tr>
          }
        >
          {shown.map((coupon) => (
            <tr key={coupon.id} className="hover:bg-mist/60">
              <Td>
                <Link
                  to={`/admin/coupons/${coupon.id}`}
                  className="font-medium text-brand-700 underline-offset-4 hover:underline"
                >
                  {coupon.code}
                </Link>
                {coupon.description && <p className="text-sm text-muted">{coupon.description}</p>}
              </Td>
              <Td className="text-sm text-ink-soft">
                {couponBenefit(coupon, t, locale)}
                {coupon.minOrder && (
                  <span className="block text-muted">
                    {t('admin.coupons.fromOrder', { amount: formatMoney(coupon.minOrder, locale) })}
                  </span>
                )}
              </Td>
              <Td className="text-sm text-ink-soft">{couponValidity(coupon, t, locale)}</Td>
              <Td className="text-right text-sm tabular-nums">
                {coupon.usageLimitTotal
                  ? t('admin.coupons.usedOf', {
                      count: coupon.timesRedeemed,
                      used: formatCount(coupon.timesRedeemed, locale),
                      limit: formatCount(coupon.usageLimitTotal, locale),
                    })
                  : formatCount(coupon.timesRedeemed, locale)}
              </Td>
              <Td>
                <CouponStatusPill status={coupon.status} />
              </Td>
            </tr>
          ))}
        </DataTable>
      )}

      <CouponDialog
        key={dialogKey}
        coupon={creating ? 'new' : null}
        onClose={() => setCreating(false)}
        onSaved={(coupon) => void navigate(`/admin/coupons/${coupon.id}`)}
      />
    </>
  );
}
