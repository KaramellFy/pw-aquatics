import { Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { Pagination } from '@/components/ui/Pagination';
import { Price } from '@/components/ui/Price';
import { formatInstantDate } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { useAdminCustomers, type CustomerStatus } from '../api/customers';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { DataTable, Td, Th } from '../components/DataTable';
import { FilterTabs } from '../components/FilterTabs';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';

type StatusFilter = 'ALL' | 'ACTIVE' | 'LOCKED';

/** Customer accounts with search; what each has ordered and spent comes from the server. */
export function AdminCustomersPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const statusParam = params.get('status');
  const status: StatusFilter = statusParam === 'ACTIVE' || statusParam === 'LOCKED' ? statusParam : 'ALL';
  const page = Number(params.get('page') ?? '0');
  const [search, setSearch] = useState(q);
  const customers = useAdminCustomers({
    q,
    status: status === 'ALL' ? undefined : (status as CustomerStatus),
    page,
  });

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };
  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    update({ q: search.trim() || undefined });
  };

  return (
    <>
      <AdminPageHeader title={t('admin.customers.title')} lead={t('admin.customers.lead')} />
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <form role="search" onSubmit={onSearch} className="flex w-full max-w-xl items-end gap-2">
          <div className="flex-1 space-y-2">
            <label htmlFor="admin-customer-search" className="block text-sm font-medium text-ink-soft">
              {t('admin.common.search')}
            </label>
            <input
              id="admin-customer-search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('admin.customers.searchPlaceholder')}
              maxLength={100}
              className="block h-12 w-full rounded-xl border border-line-strong bg-surface px-4 text-base focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none"
            />
          </div>
          <button
            type="submit"
            aria-label={t('admin.common.search')}
            className="inline-flex size-12 items-center justify-center rounded-xl bg-ink text-white hover:bg-ink-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            <Search aria-hidden className="size-5" />
          </button>
        </form>
        <FilterTabs
          id="customer-status"
          label={t('admin.customers.statusFilter')}
          value={status}
          onChange={(next) => update({ status: next === 'ALL' ? undefined : next })}
          options={(['ALL', 'ACTIVE', 'LOCKED'] as const).map((value) => ({
            value,
            label: t(`admin.customers.filters.${value}`),
          }))}
        />
      </div>

      <LoadState
        isPending={customers.isPending}
        isError={customers.isError}
        hasData={Boolean(customers.data)}
        onRetry={() => void customers.refetch()}
      />
      {customers.data && customers.data.items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t('admin.customers.empty')}
        </p>
      )}
      {customers.data && customers.data.items.length > 0 && (
        <>
          <DataTable
            caption={t('admin.customers.title')}
            head={
              <tr>
                <Th>{t('admin.customers.columns.customer')}</Th>
                <Th>{t('admin.customers.columns.registered')}</Th>
                <Th className="text-right">{t('admin.customers.columns.orders')}</Th>
                <Th className="text-right">{t('admin.customers.columns.spent')}</Th>
                <Th>{t('admin.customers.columns.lastOrder')}</Th>
              </tr>
            }
          >
            {customers.data.items.map((customer) => (
              <tr key={customer.id} className="hover:bg-mist/60">
                <Td>
                  <div className="flex flex-wrap items-center gap-2">
                    <Link
                      to={`/admin/customers/${customer.id}`}
                      className="font-medium text-ink underline-offset-4 hover:underline"
                    >
                      {customer.name}
                    </Link>
                    {customer.status === 'LOCKED' && (
                      <Pill tone="warning">{t('admin.customers.locked')}</Pill>
                    )}
                    {!customer.emailVerified && <Pill>{t('admin.customers.unverified')}</Pill>}
                  </div>
                  <p className="max-w-[18rem] truncate text-sm text-muted">{customer.email}</p>
                </Td>
                <Td className="text-sm whitespace-nowrap text-ink-soft">
                  {formatInstantDate(customer.registeredAt, locale, 'medium')}
                </Td>
                <Td className="text-right tabular-nums">{customer.orderCount}</Td>
                <Td className="text-right">
                  <Price value={customer.lifetimeValue} />
                </Td>
                <Td className="text-sm whitespace-nowrap text-ink-soft">
                  {customer.lastOrderAt
                    ? formatInstantDate(customer.lastOrderAt, locale, 'medium')
                    : t('admin.common.none')}
                </Td>
              </tr>
            ))}
          </DataTable>
          <div className="mt-6">
            <Pagination
              page={customers.data.page + 1}
              totalPages={customers.data.totalPages}
              onChange={(next) => update({ page: String(next - 1) })}
            />
          </div>
        </>
      )}
    </>
  );
}
