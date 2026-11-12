import { useTranslation } from 'react-i18next';
import { formatMoney } from '@/lib/format/money';
import { formatCount, formatCountCompact } from '@/lib/format/number';
import type { ProductStats, SalesStats, StockStats } from '../../api/statistics';
import { ChartCard, ChartTable } from '../charts/ChartCard';
import { ColumnChart } from '../charts/ColumnChart';
import { RankedBars } from '../charts/RankedBars';

function useLocale() {
  const { i18n } = useTranslation();
  return i18n.resolvedLanguage ?? 'en';
}

/** Best sellers by revenue, with the number of animals sold. */
export function TopProducts({ stats, dimmed }: { stats: ProductStats; dimmed: boolean }) {
  const { t } = useTranslation();
  const locale = useLocale();
  return (
    <ChartCard
      id="top-products"
      title={t('admin.dashboard.products.title')}
      description={t('admin.dashboard.products.description')}
      dimmed={dimmed}
    >
      <RankedBars
        label={t('admin.dashboard.products.title')}
        emptyText={t('admin.dashboard.noSales')}
        rows={stats.topProducts.map((row, index) => ({
          key: row.productId ?? `removed-${index}`,
          label: row.gradeName ? `${row.name} · ${row.gradeName}` : row.name,
          value: row.revenue.amount,
          display: formatMoney(row.revenue, locale),
          secondary: t('admin.dashboard.units', {
            count: row.units,
            formatted: formatCount(row.units, locale),
          }),
        }))}
      />
    </ChartCard>
  );
}

/** Revenue of goods (without shipping) per category or per species. */
export function RevenueByGroup({
  kind,
  stats,
  dimmed,
}: {
  kind: 'category' | 'species';
  stats: ProductStats;
  dimmed: boolean;
}) {
  const { t } = useTranslation();
  const locale = useLocale();
  const rows = kind === 'category' ? stats.byCategory : stats.bySpecies;
  return (
    <ChartCard
      id={`revenue-by-${kind}`}
      title={t(`admin.dashboard.groups.${kind}.title`)}
      description={t('admin.dashboard.groups.description')}
      dimmed={dimmed}
    >
      <RankedBars
        label={t(`admin.dashboard.groups.${kind}.title`)}
        emptyText={t('admin.dashboard.noSales')}
        rows={rows.map((row, index) => ({
          key: row.name ?? `none-${index}`,
          label: row.name ?? t(`admin.dashboard.groups.${kind}.none`),
          value: row.revenue.amount,
          display: formatMoney(row.revenue, locale),
          secondary: t('admin.dashboard.units', {
            count: row.units,
            formatted: formatCount(row.units, locale),
          }),
        }))}
      />
    </ChartCard>
  );
}

/** Revenue per destination country. */
export function RevenueByCountry({ stats, dimmed }: { stats: SalesStats; dimmed: boolean }) {
  const { t } = useTranslation();
  const locale = useLocale();
  const names = new Intl.DisplayNames([locale], { type: 'region' });
  return (
    <ChartCard
      id="revenue-by-country"
      title={t('admin.dashboard.countries.title')}
      description={t('admin.dashboard.countries.description')}
      dimmed={dimmed}
    >
      <RankedBars
        label={t('admin.dashboard.countries.title')}
        emptyText={t('admin.dashboard.noSales')}
        rows={stats.byCountry.map((row) => ({
          key: row.countryCode,
          label: names.of(row.countryCode) ?? row.countryCode,
          value: row.revenue.amount,
          display: formatMoney(row.revenue, locale),
          secondary: t('admin.dashboard.orders', {
            count: row.orders,
            formatted: formatCount(row.orders, locale),
          }),
        }))}
      />
    </ChartCard>
  );
}

/** Orders per weekday: when customers order (dispatch is Monday to Wednesday). */
export function OrdersByWeekday({ stats, dimmed }: { stats: SalesStats; dimmed: boolean }) {
  const { t } = useTranslation();
  const locale = useLocale();
  // 2024-01-01 was a Monday; only the weekday name is used.
  const dayName = (weekday: number, style: 'short' | 'long') =>
    new Intl.DateTimeFormat(locale, { weekday: style, timeZone: 'UTC' }).format(
      new Date(Date.UTC(2024, 0, weekday)),
    );
  const series = [
    {
      key: 'orders',
      label: t('admin.dashboard.sales.metrics.orders'),
      fillClassName: 'fill-brand-600',
      swatchClassName: 'bg-brand-600',
    },
  ];
  const points = stats.byWeekday.map((row) => ({
    key: String(row.weekday),
    axisLabel: dayName(row.weekday, 'short'),
    title: dayName(row.weekday, 'long'),
    total: row.orders,
    values: { orders: row.orders },
  }));
  return (
    <ChartCard
      id="orders-by-weekday"
      title={t('admin.dashboard.weekdays.title')}
      description={t('admin.dashboard.weekdays.description')}
      dimmed={dimmed}
      table={
        <ChartTable
          caption={t('admin.dashboard.weekdays.title')}
          columns={[
            { key: 'day', label: t('admin.dashboard.table.weekday') },
            { key: 'orders', label: t('admin.dashboard.sales.metrics.orders'), numeric: true },
            { key: 'revenue', label: t('admin.dashboard.sales.metrics.revenue'), numeric: true },
          ]}
          rows={stats.byWeekday.map((row) => ({
            key: String(row.weekday),
            cells: {
              day: dayName(row.weekday, 'long'),
              orders: formatCount(row.orders, locale),
              revenue: formatMoney(row.revenue, locale),
            },
          }))}
        />
      }
    >
      <ColumnChart
        label={t('admin.dashboard.weekdays.title')}
        series={series}
        points={points}
        formatValue={(value) => formatCount(value, locale)}
        formatTick={(value) => formatCountCompact(value, locale)}
        integer
        height={200}
      />
    </ChartCard>
  );
}

/** All orders placed in the period by their current status, cancelled ones included. */
export function OrdersByStatus({ stats, dimmed }: { stats: SalesStats; dimmed: boolean }) {
  const { t } = useTranslation();
  const locale = useLocale();
  return (
    <ChartCard
      id="orders-by-status"
      title={t('admin.dashboard.status.title')}
      description={t('admin.dashboard.status.description')}
      dimmed={dimmed}
    >
      <RankedBars
        label={t('admin.dashboard.status.title')}
        emptyText={t('admin.dashboard.noOrders')}
        rows={stats.byStatus.map((row) => ({
          key: row.status,
          label: t(`orders.status.${row.status}`),
          value: row.orders,
          display: formatCount(row.orders, locale),
          secondary: formatMoney(row.total, locale),
        }))}
      />
    </ChartCard>
  );
}

/** Stock added and removed per reason, from the stock ledger. */
export function StockMovements({ stats, dimmed }: { stats: StockStats; dimmed: boolean }) {
  const { t } = useTranslation();
  const locale = useLocale();
  return (
    <ChartCard
      id="stock-movements"
      title={t('admin.dashboard.stock.title')}
      description={t('admin.dashboard.stock.description')}
      dimmed={dimmed}
    >
      <RankedBars
        label={t('admin.dashboard.stock.title')}
        emptyText={t('admin.dashboard.stock.empty')}
        rows={stats.byReason.map((row) => ({
          key: row.reason,
          label: t(`admin.inventory.historyPage.reasons.${row.reason}`),
          value: row.units,
          display: t(row.units > 0 ? 'admin.dashboard.stock.added' : 'admin.dashboard.stock.removed', {
            formatted: formatCount(Math.abs(row.units), locale),
          }),
        }))}
      />
    </ChartCard>
  );
}
