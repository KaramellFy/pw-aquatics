import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { formatInstantDateTime } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { formatCount } from '@/lib/format/number';
import type { Dashboard } from '../../api/statistics';
import { AdminCard } from '../AdminCard';

/** The latest orders, newest first. */
export function RecentOrders({ orders }: { orders: Dashboard['recentOrders'] }) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const names = new Intl.DisplayNames([locale], { type: 'region' });
  return (
    <AdminCard id="recent-orders" title={t('admin.dashboard.recent.title')} className="min-w-0">
      {orders.length === 0 ? (
        <p className="text-sm text-muted">{t('admin.dashboard.noOrders')}</p>
      ) : (
        <ul className="divide-y divide-line">
          {orders.map((order) => (
            <li
              key={order.orderNumber}
              className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-0.5 py-3 sm:grid-cols-[10rem_minmax(0,1fr)_auto_9rem] sm:items-baseline"
            >
              <p className="font-medium text-ink tabular-nums">{order.orderNumber}</p>
              <p className="col-start-1 row-start-2 truncate text-sm text-ink-soft sm:col-start-auto sm:row-start-auto">
                {[
                  order.customerName,
                  order.countryCode ? (names.of(order.countryCode) ?? order.countryCode) : null,
                  formatInstantDateTime(order.placedAt, locale),
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
              <p className="text-right font-medium text-ink tabular-nums">
                {formatMoney(order.total, locale)}
              </p>
              <p className="text-right text-sm text-ink-soft">{t(`orders.status.${order.status}`)}</p>
            </li>
          ))}
        </ul>
      )}
    </AdminCard>
  );
}

/** Published products at or below their low-stock threshold, emptiest first. */
export function LowStockList({ products }: { products: Dashboard['lowStock'] }) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  return (
    <AdminCard
      id="low-stock"
      title={t('admin.dashboard.lowStock.title')}
      className="min-w-0"
      actions={
        <Link
          to="/admin/inventory?stock=LOW_STOCK"
          className="inline-flex min-h-11 items-center text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
        >
          {t('admin.dashboard.lowStock.all')}
        </Link>
      }
    >
      {products.length === 0 ? (
        <p className="text-sm text-muted">{t('admin.dashboard.lowStock.empty')}</p>
      ) : (
        <ul className="divide-y divide-line">
          {products.map((product) => (
            <li key={product.id} className="flex items-center justify-between gap-4 py-3">
              <Link
                to={`/admin/inventory/${product.id}`}
                className="min-w-0 truncate text-ink underline-offset-4 hover:underline"
              >
                {product.name}
              </Link>
              <span
                className={
                  product.stockUnits === 0
                    ? 'shrink-0 text-sm font-medium text-danger'
                    : 'shrink-0 text-sm font-medium text-warning'
                }
              >
                {product.stockUnits === 0
                  ? t('admin.dashboard.lowStock.soldOut')
                  : t('admin.dashboard.lowStock.left', {
                      count: product.stockUnits,
                      formatted: formatCount(product.stockUnits, locale),
                    })}
              </span>
            </li>
          ))}
        </ul>
      )}
    </AdminCard>
  );
}
