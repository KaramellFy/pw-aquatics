import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { Price } from '@/components/ui/Price';
import { AddressBlock, OrderStatusBadge } from '@/features/account';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatInstantDate, formatInstantDateTime } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useAdminCustomer, useSetCustomerLock, type CustomerDetail } from '../api/customers';
import { AdminCard } from '../components/AdminCard';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { DataTable, Td, Th } from '../components/DataTable';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';

/** One customer: profile, addresses, orders and what they have spent; lock or unlock the account. */
export function AdminCustomerDetailPage() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const customer = useAdminCustomer(id);
  const back = { to: '/admin/customers', label: t('admin.customers.back') };

  if (customer.isError && customer.error instanceof ApiError && [400, 404].includes(customer.error.status)) {
    return (
      <>
        <AdminPageHeader title={t('admin.customers.notFound')} back={back} />
        <p className="text-ink-soft">{t('admin.customers.notFoundText')}</p>
      </>
    );
  }
  return (
    <>
      <PageMeta title={t('admin.customers.metaTitle')} noIndex />
      <LoadState
        isPending={customer.isPending}
        isError={customer.isError}
        hasData={Boolean(customer.data)}
        onRetry={() => void customer.refetch()}
      />
      {customer.data && <CustomerView customer={customer.data} back={back} />}
    </>
  );
}

function CustomerView({ customer, back }: { customer: CustomerDetail; back: { to: string; label: string } }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const setLock = useSetCustomerLock(customer.id);
  const errorMessage = useApiErrorMessage();
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState('');
  const locked = customer.status === 'LOCKED';

  return (
    <>
      <AdminPageHeader
        title={`${customer.firstName} ${customer.lastName}`}
        lead={t('admin.customers.since', { date: formatInstantDate(customer.registeredAt, locale) })}
        back={back}
        actions={
          customer.lockable && (
            <Button
              variant={locked ? 'primary' : 'secondary'}
              onClick={() => {
                setReason('');
                setConfirming(true);
              }}
            >
              {t(locked ? 'admin.customers.unlock' : 'admin.customers.lock')}
            </Button>
          )
        }
      />

      {setLock.isError && (
        <Alert tone="error" className="mb-6">
          {errorMessage(setLock.error)}
        </Alert>
      )}
      {locked && (
        <Alert tone="info" className="mb-6">
          {t('admin.customers.lockedText')}
        </Alert>
      )}

      <ul className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <Figure label={t('admin.customers.figures.orders')} value={String(customer.orderCount)} />
        <Figure label={t('admin.customers.figures.paidOrders')} value={String(customer.paidOrderCount)} />
        <Figure
          label={t('admin.customers.figures.spent')}
          value={formatMoney(customer.lifetimeValue, locale)}
        />
        <Figure
          label={t('admin.customers.figures.average')}
          value={
            customer.averageOrderValue
              ? formatMoney(customer.averageOrderValue, locale)
              : t('admin.common.none')
          }
        />
      </ul>

      <div className="grid gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-8">
          <AdminCard title={t('admin.customers.orders')} id="customer-orders">
            {customer.orders.length === 0 ? (
              <p className="text-ink-soft">{t('admin.customers.noOrders')}</p>
            ) : (
              <DataTable
                caption={t('admin.customers.orders')}
                head={
                  <tr>
                    <Th>{t('admin.orders.columns.order')}</Th>
                    <Th>{t('admin.orders.columns.status')}</Th>
                    <Th className="text-right">{t('admin.orders.columns.total')}</Th>
                  </tr>
                }
              >
                {customer.orders.map((order) => (
                  <tr key={order.orderNumber} className="hover:bg-mist/60">
                    <Td>
                      <Link
                        to={`/admin/orders/${order.orderNumber}`}
                        className="font-medium text-ink tabular-nums underline-offset-4 hover:underline"
                      >
                        {order.orderNumber}
                      </Link>
                      <p className="text-sm text-muted">{formatInstantDateTime(order.placedAt, locale)}</p>
                    </Td>
                    <Td>
                      <OrderStatusBadge status={order.status} />
                    </Td>
                    <Td className="text-right">
                      <Price value={order.total} />
                      <span className="block text-xs text-muted">
                        {t('orders.packs', { count: order.itemCount })}
                      </span>
                    </Td>
                  </tr>
                ))}
              </DataTable>
            )}
          </AdminCard>
        </div>

        <div className="space-y-6 lg:col-span-4">
          <AdminCard title={t('admin.customers.profile')} id="customer-profile">
            <dl className="space-y-3 text-[0.9375rem]">
              <div>
                <dt className="text-sm text-muted">{t('admin.customers.email')}</dt>
                <dd>
                  <a
                    href={`mailto:${customer.email}`}
                    className="break-all text-brand-700 underline-offset-4 hover:underline"
                  >
                    {customer.email}
                  </a>{' '}
                  {!customer.emailVerified && <Pill>{t('admin.customers.unverified')}</Pill>}
                </dd>
              </div>
              {customer.phone && (
                <div>
                  <dt className="text-sm text-muted">{t('admin.customers.phone')}</dt>
                  <dd className="text-ink">{customer.phone}</dd>
                </div>
              )}
              <div>
                <dt className="text-sm text-muted">{t('admin.customers.language')}</dt>
                <dd className="text-ink">
                  {t(`common.languages.${customer.locale === 'de' ? 'de' : 'en'}`)}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">{t('admin.customers.lastLogin')}</dt>
                <dd className="text-ink">
                  {customer.lastLoginAt
                    ? formatInstantDateTime(customer.lastLoginAt, locale)
                    : t('admin.common.none')}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">{t('admin.customers.newsletter')}</dt>
                <dd className="text-ink">
                  {t(customer.marketingOptIn ? 'admin.customers.yes' : 'admin.customers.no')}
                </dd>
              </div>
            </dl>
          </AdminCard>

          <AdminCard title={t('admin.customers.addresses')} id="customer-addresses">
            {customer.addresses.length === 0 ? (
              <p className="text-ink-soft">{t('admin.customers.noAddresses')}</p>
            ) : (
              <ul className="space-y-5">
                {customer.addresses.map((address) => (
                  <li key={address.id}>
                    <div className="mb-1 flex flex-wrap gap-1.5">
                      {address.label && <span className="text-sm font-medium text-ink">{address.label}</span>}
                      {address.defaultShipping && <Pill>{t('admin.customers.defaultShipping')}</Pill>}
                      {address.defaultBilling && <Pill>{t('admin.customers.defaultBilling')}</Pill>}
                    </div>
                    <AddressBlock address={address} />
                  </li>
                ))}
              </ul>
            )}
          </AdminCard>
        </div>
      </div>

      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t(locked ? 'admin.customers.unlockTitle' : 'admin.customers.lockTitle', {
          name: `${customer.firstName} ${customer.lastName}`,
        })}
        description={t(locked ? 'admin.customers.unlockText' : 'admin.customers.lockText')}
        confirmLabel={t(locked ? 'admin.customers.unlock' : 'admin.customers.lock')}
        danger={!locked}
        pending={setLock.isPending}
        onConfirm={() =>
          setLock.mutate(
            { lock: !locked, reason: reason.trim() || undefined },
            { onSettled: () => setConfirming(false) },
          )
        }
      >
        {!locked && (
          <TextField
            label={`${t('admin.customers.lockReason')} (${t('admin.common.optional')})`}
            hint={t('admin.customers.lockReasonHint')}
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        )}
      </ConfirmDialog>
    </>
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
