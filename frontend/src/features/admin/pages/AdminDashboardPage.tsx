import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { formatMoney } from '@/lib/format/money';
import { formatBps, formatCount } from '@/lib/format/number';
import { formatCalendarDate } from '@/lib/format/date';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import {
  useDashboard,
  useProductStats,
  useSalesStats,
  useStockStats,
  type CountKpi,
  type MoneyKpi,
  type ShareKpi,
} from '../api/statistics';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { LoadState } from '../components/LoadState';
import { KpiTile } from '../components/charts/KpiTile';
import {
  OrdersByStatus,
  OrdersByWeekday,
  RevenueByCountry,
  RevenueByGroup,
  StockMovements,
  TopProducts,
} from '../components/dashboard/Breakdowns';
import { LowStockList, RecentOrders } from '../components/dashboard/Lists';
import { PeriodFilter } from '../components/dashboard/PeriodFilter';
import { RightNow } from '../components/dashboard/RightNow';
import { SalesOverTime } from '../components/dashboard/SalesOverTime';
import { periodFromParams, todayInShop } from '../data/periods';

/**
 * Start page of the admin area: what needs attention now, then the figures and charts of a
 * chosen period. Every number comes from the statistics API; this page only lays it out.
 */
export function AdminDashboardPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const errorMessage = useApiErrorMessage();
  const [params, setParams] = useSearchParams();
  const { period, preset } = periodFromParams(params, todayInShop());

  const dashboard = useDashboard();
  const sales = useSalesStats(period);
  const products = useProductStats(period);
  const stock = useStockStats(period);

  const money = (kpi: MoneyKpi) => ({
    value: formatMoney(kpi.value, locale),
    previous: formatMoney(kpi.previous, locale),
    changeBps: kpi.changeBps,
  });
  const count = (kpi: CountKpi) => ({
    value: formatCount(kpi.value, locale),
    previous: formatCount(kpi.previous, locale),
    changeBps: kpi.changeBps,
  });
  const share = (kpi: ShareKpi) => ({
    value: kpi.valueBps === null || kpi.valueBps === undefined ? '–' : formatBps(kpi.valueBps, locale),
    previous:
      kpi.previousBps === null || kpi.previousBps === undefined
        ? undefined
        : formatBps(kpi.previousBps, locale),
  });

  const salesData = sales.data;
  const productData = products.data;
  const stockData = stock.data;
  const failed = [sales, products, stock].find((query) => query.isError && !query.data);

  return (
    <>
      <AdminPageHeader title={t('admin.dashboard.title')} lead={t('admin.dashboard.lead')} />

      <section aria-labelledby="now-title" className="mb-12">
        <h2 id="now-title" className="mb-4 eyebrow text-muted">
          {t('admin.dashboard.now.title')}
        </h2>
        <LoadState
          isPending={dashboard.isPending}
          isError={dashboard.isError}
          hasData={Boolean(dashboard.data)}
          onRetry={() => void dashboard.refetch()}
        />
        {dashboard.data && <RightNow dashboard={dashboard.data} />}
      </section>

      <section aria-labelledby="period-title" className="space-y-6">
        <div>
          <h2 id="period-title" className="text-title">
            {t('admin.dashboard.periodTitle')}
          </h2>
          <p className="mt-1 text-sm text-ink-soft">
            {t('admin.dashboard.periodRange', {
              from: formatCalendarDate(period.from, locale),
              to: formatCalendarDate(period.to, locale),
            })}
            {salesData &&
              ` · ${t('admin.dashboard.comparedWith', {
                from: formatCalendarDate(salesData.period.previousFrom, locale),
                to: formatCalendarDate(salesData.period.previousTo, locale),
              })}`}
          </p>
        </div>

        <PeriodFilter
          key={preset ?? `${period.from}_${period.to}`}
          period={period}
          preset={preset}
          onPreset={(value) => setParams(value === '30d' ? {} : { period: value }, { replace: true })}
          onCustom={(custom) => setParams({ from: custom.from, to: custom.to }, { replace: true })}
        />

        {failed && (
          <Alert tone="error">
            <p>{errorMessage(failed.error)}</p>
            <Button
              variant="secondary"
              size="sm"
              className="mt-4"
              onClick={() => [sales, products, stock].forEach((query) => void query.refetch())}
            >
              {t('admin.common.retry')}
            </Button>
          </Alert>
        )}
        {!salesData && !failed && (
          <LoadState isPending isError={false} hasData={false} onRetry={() => undefined} />
        )}

        {salesData && (
          <ul
            aria-label={t('admin.dashboard.kpi.label')}
            className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4"
          >
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.revenue')}
                hint={t('admin.dashboard.kpi.revenueHint')}
                {...money(salesData.kpis.revenue)}
              />
            </li>
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.paidRevenue')}
                hint={t('admin.dashboard.kpi.paidRevenueHint')}
                {...money(salesData.kpis.paidRevenue)}
              />
            </li>
            <li>
              <KpiTile label={t('admin.dashboard.kpi.orders')} {...count(salesData.kpis.orders)} />
            </li>
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.averageOrderValue')}
                {...money(salesData.kpis.averageOrderValue)}
              />
            </li>
            <li>
              <KpiTile label={t('admin.dashboard.kpi.animalsSold')} {...count(salesData.kpis.animalsSold)} />
            </li>
            <li>
              <KpiTile label={t('admin.dashboard.kpi.newAccounts')} {...count(salesData.kpis.newAccounts)} />
            </li>
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.returning')}
                hint={t('admin.dashboard.kpi.returningHint')}
                {...share(salesData.kpis.returningCustomerShare)}
              />
            </li>
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.cancellationRate')}
                hint={t('admin.dashboard.kpi.cancelled', {
                  count: salesData.kpis.cancelledOrders.value,
                  formatted: formatCount(salesData.kpis.cancelledOrders.value, locale),
                })}
                {...share(salesData.kpis.cancellationRate)}
              />
            </li>
          </ul>
        )}

        {salesData && <SalesOverTime stats={salesData} dimmed={sales.isPlaceholderData} />}

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          {productData && <TopProducts stats={productData} dimmed={products.isPlaceholderData} />}
          {productData && (
            <RevenueByGroup kind="category" stats={productData} dimmed={products.isPlaceholderData} />
          )}
          {productData && (
            <RevenueByGroup kind="species" stats={productData} dimmed={products.isPlaceholderData} />
          )}
          {salesData && <RevenueByCountry stats={salesData} dimmed={sales.isPlaceholderData} />}
          {salesData && <OrdersByWeekday stats={salesData} dimmed={sales.isPlaceholderData} />}
          {salesData && <OrdersByStatus stats={salesData} dimmed={sales.isPlaceholderData} />}
        </div>
      </section>

      <section aria-labelledby="stock-title" className="mt-12 space-y-6">
        <h2 id="stock-title" className="text-title">
          {t('admin.dashboard.stockTitle')}
        </h2>
        {stockData && (
          <ul
            aria-label={t('admin.dashboard.kpi.stockLabel')}
            className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4"
          >
            <li>
              <KpiTile label={t('admin.dashboard.kpi.restocked')} {...count(stockData.restocked)} />
            </li>
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.soldFromStock')}
                hint={t('admin.dashboard.kpi.soldFromStockHint')}
                {...count(stockData.sold)}
              />
            </li>
            <li>
              <KpiTile label={t('admin.dashboard.kpi.lost')} upIsGood={false} {...count(stockData.lost)} />
            </li>
            <li>
              <KpiTile
                label={t('admin.dashboard.kpi.lossRate')}
                hint={t('admin.dashboard.kpi.lossRateHint')}
                {...share(stockData.lossRate)}
              />
            </li>
          </ul>
        )}
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          {stockData && <StockMovements stats={stockData} dimmed={stock.isPlaceholderData} />}
          {dashboard.data && <LowStockList products={dashboard.data.lowStock} />}
        </div>
      </section>

      {dashboard.data && (
        <div className="mt-12">
          <RecentOrders orders={dashboard.data.recentOrders} />
        </div>
      )}
    </>
  );
}
