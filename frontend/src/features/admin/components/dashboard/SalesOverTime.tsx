import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { cn } from '@/lib/cn';
import { formatCalendarDateWith } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { formatCount, formatCountCompact, formatMinorUnitsCompact } from '@/lib/format/number';
import type { Granularity, SalesBucket, SalesStats } from '../../api/statistics';
import { ChartCard, ChartTable, Legend } from '../charts/ChartCard';
import { ColumnChart, type ColumnSeries } from '../charts/ColumnChart';

const metrics = ['revenue', 'orders', 'animals'] as const;
type Metric = (typeof metrics)[number];

/** Paid (darker) and awaiting payment (lighter): two steps of the brand blue, checked for contrast. */
const SERIES = [
  { key: 'paid', fillClassName: 'fill-brand-800', swatchClassName: 'bg-brand-800' },
  { key: 'awaiting', fillClassName: 'fill-brand-500', swatchClassName: 'bg-brand-500' },
] as const;

function pick(bucket: SalesBucket, metric: Metric) {
  switch (metric) {
    case 'revenue':
      return {
        total: bucket.revenue.amount,
        paid: bucket.paidRevenue.amount,
        awaiting: bucket.awaitingRevenue.amount,
      };
    case 'orders':
      return { total: bucket.orders, paid: bucket.paidOrders, awaiting: bucket.awaitingOrders };
    case 'animals':
      return { total: bucket.animals, paid: bucket.paidAnimals, awaiting: bucket.awaitingAnimals };
  }
}

/** Labels of a bucket: short under the column, full in the tooltip and table. */
export function useBucketLabels(granularity: Granularity) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  return {
    axis: (start: string) =>
      granularity === 'MONTH'
        ? formatCalendarDateWith(start, locale, { month: 'short', year: '2-digit' })
        : formatCalendarDateWith(start, locale, { day: 'numeric', month: 'short' }),
    full: (start: string) =>
      granularity === 'DAY'
        ? formatCalendarDateWith(start, locale, {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
            year: 'numeric',
          })
        : granularity === 'WEEK'
          ? t('admin.dashboard.sales.weekOf', {
              date: formatCalendarDateWith(start, locale, { day: 'numeric', month: 'long', year: 'numeric' }),
            })
          : formatCalendarDateWith(start, locale, { month: 'long', year: 'numeric' }),
  };
}

/** Sales over time as stacked columns, switchable between revenue, orders and animals. */
export function SalesOverTime({ stats, dimmed }: { stats: SalesStats; dimmed: boolean }) {
  const switchId = useId();
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const [metric, setMetric] = useState<Metric>('revenue');
  const labels = useBucketLabels(stats.period.granularity);

  const format = (value: number) =>
    metric === 'revenue'
      ? formatMoney({ amount: value, currency: 'EUR' }, locale)
      : formatCount(value, locale);
  const formatTick = (value: number) =>
    metric === 'revenue' ? formatMinorUnitsCompact(value, 'EUR', locale) : formatCountCompact(value, locale);

  const series: ColumnSeries[] = SERIES.map((item) => ({
    ...item,
    label: t(`admin.dashboard.sales.series.${item.key}`),
  }));
  const points = stats.series.map((bucket) => {
    const values = pick(bucket, metric);
    return {
      key: bucket.start,
      axisLabel: labels.axis(bucket.start),
      title: labels.full(bucket.start),
      total: values.total,
      values: { paid: values.paid, awaiting: values.awaiting },
    };
  });

  return (
    <ChartCard
      id="sales-over-time"
      title={t('admin.dashboard.sales.title')}
      description={t(`admin.dashboard.sales.granularity.${stats.period.granularity}`)}
      dimmed={dimmed}
      controls={
        <div
          role="group"
          aria-label={t('admin.dashboard.sales.metricLabel')}
          className="inline-flex rounded-full border border-line p-1"
        >
          {metrics.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={metric === value}
              onClick={() => setMetric(value)}
              className={cn(
                'relative min-h-9 press rounded-full px-4 text-sm transition-colors',
                metric === value ? 'font-medium text-white' : 'text-ink-soft hover:bg-ink/5',
              )}
            >
              {metric === value && (
                <SlidingIndicator
                  layoutId={`sales-metric-${switchId}`}
                  className="inset-0 rounded-full"
                  markClassName="bg-ink"
                />
              )}
              <span className="relative z-10">{t(`admin.dashboard.sales.metrics.${value}`)}</span>
            </button>
          ))}
        </div>
      }
      legend={<Legend items={series} />}
      table={
        <ChartTable
          caption={t('admin.dashboard.sales.title')}
          columns={[
            { key: 'period', label: t('admin.dashboard.table.period') },
            { key: 'total', label: t('admin.dashboard.table.total'), numeric: true },
            { key: 'paid', label: series[0]!.label, numeric: true },
            { key: 'awaiting', label: series[1]!.label, numeric: true },
          ]}
          rows={points.map((point) => ({
            key: point.key,
            cells: {
              period: point.title,
              total: format(point.total),
              paid: format(point.values.paid),
              awaiting: format(point.values.awaiting),
            },
          }))}
        />
      }
    >
      <ColumnChart
        label={t('admin.dashboard.sales.chartLabel', {
          metric: t(`admin.dashboard.sales.metrics.${metric}`),
        })}
        series={series}
        points={points}
        formatValue={format}
        formatTick={formatTick}
        totalLabel={t('admin.dashboard.table.total')}
        integer={metric !== 'revenue'}
        emptyTop={metric === 'revenue' ? 10_000 : 1}
      />
    </ChartCard>
  );
}
