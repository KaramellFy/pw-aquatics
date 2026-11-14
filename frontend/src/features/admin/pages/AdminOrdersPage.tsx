import { Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { TextField } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Price } from '@/components/ui/Price';
import { OrderStatusBadge } from '@/features/account';
import { formatCalendarDate, formatInstantDateTime } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { useAdminOrders, type AdminOrderStatus, type OrderListQuery } from '../api/orders';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { DataTable, Td, Th } from '../components/DataTable';
import { FilterTabs } from '../components/FilterTabs';
import { LoadState } from '../components/LoadState';
import { Pill } from '../components/StatusBadge';

/** Quick filters. "To ship" and "awaiting payment" list the oldest first: they are work queues. */
const ORDER_VIEWS = {
  all: { statuses: [], oldestFirst: false },
  toShip: { statuses: ['PAID', 'PROCESSING'], oldestFirst: true },
  awaitingPayment: { statuses: ['PENDING_PAYMENT'], oldestFirst: true },
  shipped: { statuses: ['SHIPPED'], oldestFirst: false },
  delivered: { statuses: ['DELIVERED'], oldestFirst: false },
  cancelled: { statuses: ['CANCELLED'], oldestFirst: false },
} as const satisfies Record<string, { statuses: readonly AdminOrderStatus[]; oldestFirst: boolean }>;

type OrderView = keyof typeof ORDER_VIEWS;

function isOrderView(value: string | null): value is OrderView {
  return value !== null && value in ORDER_VIEWS;
}

/** All orders with quick filters, search and a date range. Filters live in the URL. */
export function AdminOrdersPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [params, setParams] = useSearchParams();
  const viewParam = params.get('view');
  const view: OrderView = isOrderView(viewParam) ? viewParam : 'all';
  const q = params.get('q') ?? '';
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const page = Number(params.get('page') ?? '0');
  const [search, setSearch] = useState(q);

  const query: OrderListQuery = {
    q,
    statuses: [...ORDER_VIEWS[view].statuses],
    oldestFirst: ORDER_VIEWS[view].oldestFirst,
    from: from || undefined,
    to: to || undefined,
    page,
  };
  const orders = useAdminOrders(query);

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
      <AdminPageHeader title={t('admin.orders.title')} lead={t('admin.orders.lead')} />

      <FilterTabs
        id="order-views"
        label={t('admin.orders.views.label')}
        value={view}
        onChange={(next) => update({ view: next === 'all' ? undefined : next })}
        options={(Object.keys(ORDER_VIEWS) as OrderView[]).map((value) => ({
          value,
          label: t(`admin.orders.views.${value}`),
        }))}
        className="mb-5"
      />

      <div className="mb-6 grid gap-4 md:grid-cols-[1fr_auto_auto] md:items-end">
        <form role="search" onSubmit={onSearch} className="flex items-end gap-2">
          <div className="flex-1 space-y-2">
            <label htmlFor="admin-order-search" className="block text-sm font-medium text-ink-soft">
              {t('admin.common.search')}
            </label>
            <input
              id="admin-order-search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('admin.orders.searchPlaceholder')}
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
        <TextField
          type="date"
          label={t('admin.orders.from')}
          value={from}
          onChange={(event) => update({ from: event.target.value || undefined })}
        />
        <TextField
          type="date"
          label={t('admin.orders.to')}
          value={to}
          onChange={(event) => update({ to: event.target.value || undefined })}
        />
      </div>

      <LoadState
        isPending={orders.isPending}
        isError={orders.isError}
        hasData={Boolean(orders.data)}
        onRetry={() => void orders.refetch()}
      />

      {orders.data && orders.data.items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t(view === 'toShip' ? 'admin.orders.emptyToShip' : 'admin.orders.empty')}
        </p>
      )}

      {orders.data && orders.data.items.length > 0 && (
        <>
          <p className="mb-3 text-sm text-muted" role="status">
            {t('admin.orders.count', { count: orders.data.totalItems })}
          </p>
          <DataTable
            caption={t('admin.orders.title')}
            head={
              <tr>
                <Th>{t('admin.orders.columns.order')}</Th>
                <Th>{t('admin.orders.columns.customer')}</Th>
                <Th>{t('admin.orders.columns.status')}</Th>
                <Th className="text-right">{t('admin.orders.columns.total')}</Th>
                <Th>{t('admin.orders.columns.dispatch')}</Th>
              </tr>
            }
          >
            {orders.data.items.map((order) => (
              <tr key={order.orderNumber} className="hover:bg-mist/60">
                <Td>
                  <Link
                    to={`/admin/orders/${order.orderNumber}`}
                    className="font-medium text-ink tabular-nums underline-offset-4 hover:underline"
                  >
                    {order.orderNumber}
                  </Link>
                  <p className="text-sm whitespace-nowrap text-muted">
                    {formatInstantDateTime(order.placedAt, locale)}
                  </p>
                </Td>
                <Td>
                  <p className="text-ink">{order.customerName}</p>
                  <p className="max-w-[16rem] truncate text-sm text-muted">
                    {order.customerEmail} · {order.countryCode}
                  </p>
                </Td>
                <Td>
                  <div className="flex flex-wrap items-center gap-1.5">
                    <OrderStatusBadge status={order.status} />
                    {order.containsLiveAnimals && <Pill>{t('admin.orders.live')}</Pill>}
                  </div>
                  <p className="mt-1 text-xs text-muted">
                    {t(`admin.orders.paymentMethod.${order.paymentMethod}`)} ·{' '}
                    {t(`admin.orders.paymentStatus.${order.paymentStatus}`)}
                  </p>
                </Td>
                <Td className="text-right">
                  <Price value={order.total} />
                  <span className="block text-xs text-muted">
                    {t('orders.packs', { count: order.itemCount })}
                  </span>
                </Td>
                <Td className="text-sm whitespace-nowrap text-ink-soft">
                  {order.plannedDispatchDate && order.status !== 'CANCELLED'
                    ? formatCalendarDate(order.plannedDispatchDate, locale, 'medium')
                    : t('admin.common.none')}
                </Td>
              </tr>
            ))}
          </DataTable>
          <div className="mt-6">
            <Pagination
              page={orders.data.page + 1}
              totalPages={orders.data.totalPages}
              onChange={(next) => update({ page: String(next - 1) })}
            />
          </div>
        </>
      )}
    </>
  );
}
