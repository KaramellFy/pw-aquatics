import { ArrowRight } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Pagination } from '@/components/ui/Pagination';
import { Price } from '@/components/ui/Price';
import { formatInstantDate } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { useOrders } from '../api/orders';
import { OrderStatusBadge } from '../components/OrderStatusBadge';

/** The customer's order history, newest first. */
export function OrdersPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [page, setPage] = useState(0);
  const orders = useOrders(page);

  return (
    <>
      <h1 className="text-headline">{t('orders.title')}</h1>
      <p className="mt-3 text-lg text-ink-soft">{t('orders.lead')}</p>

      {orders.isPending && (
        <p role="status" className="mt-10 text-muted">
          {t('common.loading')}
        </p>
      )}
      {orders.isError && !orders.data && (
        <Alert tone="error" className="mt-10">
          <p>{t('orders.loadError')}</p>
          <Button variant="secondary" size="sm" className="mt-4" onClick={() => void orders.refetch()}>
            {t('shop.error.retry')}
          </Button>
        </Alert>
      )}

      {orders.data && orders.data.totalItems === 0 && (
        <div className="mt-10 rounded-card border border-line bg-mist px-6 py-14 text-center">
          <h2 className="text-title">{t('orders.empty.title')}</h2>
          <p className="mx-auto mt-3 max-w-md text-ink-soft">{t('orders.empty.text')}</p>
          <ButtonLink to="/shop" className="mt-8">
            {t('orders.empty.cta')}
          </ButtonLink>
        </div>
      )}

      {orders.data && orders.data.items.length > 0 && (
        <>
          <ul className="mt-10 space-y-4">
            {orders.data.items.map((order) => (
              <li key={order.orderNumber} className="rounded-card border border-line bg-surface p-6">
                <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
                  <div>
                    <h2 className="font-display text-xl font-normal">
                      {t('orders.number', { number: order.orderNumber })}
                    </h2>
                    <p className="mt-1 text-sm text-muted">
                      {t('orders.placedOn', { date: formatInstantDate(order.placedAt, locale) })}
                    </p>
                  </div>
                  <OrderStatusBadge status={order.status} />
                </div>
                <p className="mt-4 text-ink-soft">{order.productNames.join(', ')}</p>
                <div className="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-4">
                  <p className="text-sm text-ink-soft">
                    {t('orders.packs', { count: order.itemCount })} ·{' '}
                    <Price value={order.total} className="font-medium text-ink" />
                  </p>
                  <Link
                    to={`/account/orders/${order.orderNumber}`}
                    aria-label={t('orders.viewLabel', { number: order.orderNumber })}
                    className="group inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
                  >
                    {t('orders.view')}
                    <ArrowRight
                      aria-hidden
                      className="size-4 transition-transform group-hover:translate-x-0.5"
                    />
                  </Link>
                </div>
              </li>
            ))}
          </ul>
          <div className="mt-10">
            <Pagination
              page={orders.data.page + 1}
              totalPages={orders.data.totalPages}
              onChange={(next) => setPage(next - 1)}
            />
          </div>
        </>
      )}
    </>
  );
}
