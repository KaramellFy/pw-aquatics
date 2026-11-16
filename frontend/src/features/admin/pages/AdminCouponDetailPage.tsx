import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Pagination } from '@/components/ui/Pagination';
import { Price } from '@/components/ui/Price';
import { OrderStatusBadge } from '@/features/account';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { couponBenefit } from '@/lib/format/coupon';
import { formatInstantDateTime } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { formatCount } from '@/lib/format/number';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import {
  useAdminCoupon,
  useCouponRedemptions,
  useCouponUsage,
  useDeleteCoupon,
  useSaveCoupon,
  type AdminCoupon,
} from '../api/coupons';
import { AdminCard } from '../components/AdminCard';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { CouponDialog } from '../components/CouponDialog';
import { CouponStatusPill } from '../components/CouponStatusPill';
import { DataTable, Td, Th } from '../components/DataTable';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';
import { couponValidity } from '../data/coupons';

/** One coupon: its terms, what it has brought and the orders that used it. */
export function AdminCouponDetailPage() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const coupon = useAdminCoupon(id);
  const back = { to: '/admin/coupons', label: t('admin.coupons.back') };

  if (coupon.isError && coupon.error instanceof ApiError && [400, 404].includes(coupon.error.status)) {
    return (
      <>
        <AdminPageHeader title={t('admin.coupons.notFound')} back={back} />
        <p className="text-ink-soft">{t('admin.coupons.notFoundText')}</p>
      </>
    );
  }
  return (
    <>
      <PageMeta title={t('admin.coupons.metaTitle')} noIndex />
      <LoadState
        isPending={coupon.isPending}
        isError={coupon.isError}
        hasData={Boolean(coupon.data)}
        onRetry={() => void coupon.refetch()}
      />
      {coupon.data && <CouponView coupon={coupon.data} back={back} />}
    </>
  );
}

function CouponView({ coupon, back }: { coupon: AdminCoupon; back: { to: string; label: string } }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const navigate = useNavigate();
  const usage = useCouponUsage(coupon.id);
  const save = useSaveCoupon();
  const remove = useDeleteCoupon();
  const errorMessage = useApiErrorMessage();
  const [editing, setEditing] = useState(false);
  const [editKey, setEditKey] = useState(0);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Switching on or off sends the terms as they are, with the loaded version.
  const toggleActive = () => {
    if (save.isPending) return;
    save.mutate({
      id: coupon.id,
      input: {
        description: coupon.description ?? undefined,
        type: coupon.type,
        value: coupon.percentage ?? coupon.amount?.amount ?? 0,
        minOrderCents: coupon.minOrder?.amount ?? 0,
        maxDiscountCents: coupon.maxDiscount?.amount,
        startsOn: coupon.startsOn ?? undefined,
        endsOn: coupon.endsOn ?? undefined,
        usageLimitTotal: coupon.usageLimitTotal ?? undefined,
        usageLimitPerCustomer: coupon.usageLimitPerCustomer ?? undefined,
        active: !coupon.active,
        version: coupon.version,
      },
    });
  };

  const terms: [string, string][] = [
    [t('admin.coupons.columns.benefit'), couponBenefit(coupon, t, locale)],
    [
      t('admin.coupons.terms.maxDiscount'),
      coupon.maxDiscount ? formatMoney(coupon.maxDiscount, locale) : t('admin.common.none'),
    ],
    [
      t('admin.coupons.terms.minOrder'),
      coupon.minOrder ? formatMoney(coupon.minOrder, locale) : t('admin.common.none'),
    ],
    [t('admin.coupons.columns.valid'), couponValidity(coupon, t, locale)],
    [
      t('admin.coupons.terms.limitTotal'),
      coupon.usageLimitTotal
        ? formatCount(coupon.usageLimitTotal, locale)
        : t('admin.coupons.terms.unlimited'),
    ],
    [
      t('admin.coupons.terms.limitPerCustomer'),
      coupon.usageLimitPerCustomer
        ? formatCount(coupon.usageLimitPerCustomer, locale)
        : t('admin.coupons.terms.unlimited'),
    ],
  ];

  return (
    <>
      <AdminPageHeader
        title={coupon.code}
        lead={coupon.description ?? undefined}
        back={back}
        actions={
          <div className="flex flex-wrap gap-3">
            <Button
              variant="secondary"
              onClick={toggleActive}
              aria-disabled={save.isPending || undefined}
              aria-busy={save.isPending || undefined}
            >
              {coupon.active ? t('admin.coupons.switchOff') : t('admin.coupons.switchOn')}
            </Button>
            <Button
              onClick={() => {
                setEditKey((key) => key + 1);
                setEditing(true);
              }}
            >
              {t('admin.common.edit')}
            </Button>
          </div>
        }
      />
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <CouponStatusPill status={coupon.status} />
        <p className="text-sm text-ink-soft">{t('admin.coupons.rulesShort')}</p>
      </div>
      {save.isError && (
        <Alert tone="error" className="mb-6">
          {errorMessage(save.error)}
        </Alert>
      )}

      <ul className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
        <Figure
          label={t('admin.coupons.figures.orders')}
          value={usage.data ? formatCount(usage.data.orders, locale) : '–'}
        />
        <Figure
          label={t('admin.coupons.figures.saving')}
          value={usage.data ? formatMoney(usage.data.saving, locale) : '–'}
        />
        <Figure
          label={t('admin.coupons.figures.revenue')}
          value={usage.data ? formatMoney(usage.data.revenue, locale) : '–'}
        />
      </ul>

      {/* The orders table needs the full width; terms and delete sit side by side above it. */}
      <div className="mb-6 grid gap-6 lg:grid-cols-12">
        <div className="lg:col-span-7">
          <AdminCard title={t('admin.coupons.terms.title')} id="coupon-terms">
            <dl className="space-y-3 text-sm">
              {terms.map(([label, value]) => (
                <div key={label} className="flex justify-between gap-4">
                  <dt className="text-ink-soft">{label}</dt>
                  <dd className="text-right text-ink">{value}</dd>
                </div>
              ))}
            </dl>
          </AdminCard>
        </div>
        <div className="lg:col-span-5">
          <AdminCard title={t('admin.coupons.delete.title')} id="coupon-delete">
            <p className="text-sm text-ink-soft">{t('admin.coupons.delete.text')}</p>
            {remove.isError && (
              <Alert tone="error" className="mt-4">
                {errorMessage(remove.error)}
              </Alert>
            )}
            <Button variant="secondary" className="mt-4" onClick={() => setConfirmDelete(true)}>
              {t('admin.coupons.delete.action')}
            </Button>
          </AdminCard>
        </div>
      </div>
      <Redemptions couponId={coupon.id} />

      <CouponDialog key={editKey} coupon={editing ? coupon : null} onClose={() => setEditing(false)} />
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={t('admin.coupons.delete.confirmTitle', { code: coupon.code })}
        description={t('admin.coupons.delete.confirmText')}
        confirmLabel={t('admin.coupons.delete.action')}
        danger
        pending={remove.isPending}
        onConfirm={() =>
          remove.mutate(coupon.id, {
            onSuccess: () => void navigate('/admin/coupons', { replace: true }),
            onSettled: () => setConfirmDelete(false),
          })
        }
      />
    </>
  );
}

function Redemptions({ couponId }: { couponId: string }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [page, setPage] = useState(0);
  const redemptions = useCouponRedemptions(couponId, page);

  return (
    <AdminCard title={t('admin.coupons.redemptions.title')} id="coupon-redemptions">
      <LoadState
        isPending={redemptions.isPending}
        isError={redemptions.isError}
        hasData={Boolean(redemptions.data)}
        onRetry={() => void redemptions.refetch()}
      />
      {redemptions.data && redemptions.data.items.length === 0 && (
        <p className="text-ink-soft">{t('admin.coupons.redemptions.empty')}</p>
      )}
      {redemptions.data && redemptions.data.items.length > 0 && (
        <>
          <DataTable
            caption={t('admin.coupons.redemptions.title')}
            head={
              <tr>
                <Th>{t('admin.orders.columns.order')}</Th>
                <Th>{t('admin.orders.columns.status')}</Th>
                <Th className="text-right">{t('admin.coupons.redemptions.saving')}</Th>
                <Th className="text-right">{t('admin.orders.columns.total')}</Th>
              </tr>
            }
          >
            {redemptions.data.items.map((row) => (
              <tr key={row.orderNumber} className="hover:bg-mist/60">
                <Td>
                  <Link
                    to={`/admin/orders/${row.orderNumber}`}
                    className="font-medium text-ink tabular-nums underline-offset-4 hover:underline"
                  >
                    {row.orderNumber}
                  </Link>
                  <p className="text-sm text-muted">
                    {[row.customerName, formatInstantDateTime(row.placedAt, locale)]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                </Td>
                <Td>
                  <div className="flex flex-wrap items-center gap-2">
                    <OrderStatusBadge status={row.orderStatus} />
                    {row.released && <Pill>{t('admin.coupons.redemptions.released')}</Pill>}
                  </div>
                </Td>
                <Td className="text-right">
                  <Price value={row.saving} />
                </Td>
                <Td className="text-right">
                  <Price value={row.orderTotal} />
                </Td>
              </tr>
            ))}
          </DataTable>
          <div className="mt-4">
            <Pagination
              page={page + 1}
              totalPages={redemptions.data.totalPages}
              onChange={(next) => setPage(next - 1)}
            />
          </div>
        </>
      )}
    </AdminCard>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <li className="rounded-card border border-line bg-surface p-4 sm:p-5">
      <p className="text-sm text-ink-soft">{label}</p>
      <p className="mt-2 text-xl leading-tight font-semibold break-words text-ink sm:text-[1.75rem]">
        {value}
      </p>
    </li>
  );
}
