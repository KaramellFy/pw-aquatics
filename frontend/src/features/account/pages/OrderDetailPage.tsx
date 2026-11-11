import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Price } from '@/components/ui/Price';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatCalendarDate, formatInstantDate } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useCancelOrder, useOrder, type Order } from '../api/orders';
import { AddressBlock } from '../components/AddressBlock';
import { OrderStatusBadge } from '../components/OrderStatusBadge';
import { OrderStatusTimeline } from '../components/OrderStatusTimeline';
import { OrderTotalsList } from '../components/OrderTotalsList';
import { PaymentInstructionsCard } from '../components/PaymentInstructionsCard';

export function OrderDetailPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const order = useOrder(orderNumber);

  const backLink = (
    <Link
      to="/account/orders"
      className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
    >
      <ArrowLeft aria-hidden className="size-4" />
      {t('orders.back')}
    </Link>
  );

  if (order.isPending) {
    return (
      <p role="status" className="text-muted">
        {t('common.loading')}
      </p>
    );
  }
  if (order.isError && !order.data) {
    // Unknown numbers and other customers' orders look the same: not found.
    const notFound =
      order.error instanceof ApiError && (order.error.status === 404 || order.error.status === 400);
    return (
      <>
        {backLink}
        {notFound ? (
          <div className="mt-6 rounded-card border border-line bg-mist px-6 py-14 text-center">
            <h1 className="text-title">{t('orders.detail.notFoundTitle')}</h1>
            <p className="mx-auto mt-3 max-w-md text-ink-soft">{t('orders.detail.notFoundText')}</p>
            <ButtonLink to="/account/orders" className="mt-8">
              {t('orders.back')}
            </ButtonLink>
          </div>
        ) : (
          <Alert tone="error" className="mt-6">
            <p>{t('orders.loadError')}</p>
            <Button variant="secondary" size="sm" className="mt-4" onClick={() => void order.refetch()}>
              {t('shop.error.retry')}
            </Button>
          </Alert>
        )}
      </>
    );
  }
  return (
    <>
      <PageMeta title={t('orders.detail.metaTitle', { number: order.data.orderNumber })} noIndex />
      {backLink}
      <OrderDetail order={order.data} />
    </>
  );
}

function OrderDetail({ order }: { order: Order }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const cancel = useCancelOrder();
  const errorMessage = useApiErrorMessage();
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-headline">{t('orders.number', { number: order.orderNumber })}</h1>
          <p className="mt-2 text-ink-soft">
            {t('orders.placedOn', { date: formatInstantDate(order.placedAt, locale) })}
          </p>
        </div>
        <OrderStatusBadge status={order.status} className="mt-2" />
      </div>

      {cancel.isError && (
        <Alert tone="error" className="mt-8">
          {errorMessage(cancel.error)}
        </Alert>
      )}

      {order.paymentInstructions && (
        <PaymentInstructionsCard
          instructions={order.paymentInstructions}
          dueAt={order.paymentDueAt}
          className="mt-8"
        />
      )}

      <div className="mt-10 grid gap-10 lg:grid-cols-12 lg:gap-12">
        <div className="lg:col-span-7">
          <h2 className="text-title">{t('orders.detail.items')}</h2>
          <ul className="mt-5 divide-y divide-line border-y border-line">
            {order.items.map((item) => (
              <li key={item.sku} className="flex justify-between gap-4 py-4">
                <div className="min-w-0">
                  <p className="font-display text-lg leading-snug">{item.productName}</p>
                  {item.speciesName && <p className="scientific text-sm text-muted">{item.speciesName}</p>}
                  <p className="mt-1 text-sm text-ink-soft">
                    {item.variantName}
                    {item.gradeName && <> · {item.gradeName}</>}
                  </p>
                  <p className="mt-1 text-sm text-ink-soft">
                    {t('orders.detail.quantity', { count: item.quantity })} ·{' '}
                    {t('orders.detail.each', { price: formatMoney(item.unitPrice, locale) })}
                  </p>
                </div>
                <Price value={item.lineTotal} className="shrink-0 font-medium" />
              </li>
            ))}
          </ul>
          <OrderTotalsList totals={order.totals} className="mt-6" />

          {order.customerNote && (
            <section className="mt-10">
              <h2 className="font-sans eyebrow text-brand-700">{t('orders.detail.note')}</h2>
              <p className="mt-3 whitespace-pre-line text-ink-soft">{order.customerNote}</p>
            </section>
          )}
        </div>

        <div className="space-y-8 lg:col-span-5">
          <section>
            <h2 className="font-sans eyebrow text-brand-700">{t('orders.detail.timeline')}</h2>
            <div className="mt-4">
              <OrderStatusTimeline entries={order.statusTimeline} />
            </div>
          </section>
          <section>
            <h2 className="font-sans eyebrow text-brand-700">{t('orders.detail.shipping')}</h2>
            <p className="mt-3 text-ink">{order.shippingMethodName}</p>
            {order.plannedDispatchDate && order.status !== 'CANCELLED' && (
              <p className="mt-1 text-sm text-ink-soft">
                {t('orders.detail.earliestDispatch', {
                  date: formatCalendarDate(order.plannedDispatchDate, locale, 'full'),
                })}
              </p>
            )}
          </section>
          <section>
            <h2 className="font-sans eyebrow text-brand-700">{t('orders.detail.shippingAddress')}</h2>
            <AddressBlock address={order.shippingAddress} className="mt-3" />
          </section>
          <section>
            <h2 className="font-sans eyebrow text-brand-700">{t('orders.detail.billingAddress')}</h2>
            <AddressBlock address={order.billingAddress} className="mt-3" />
          </section>
          <section>
            <h2 className="font-sans eyebrow text-brand-700">{t('orders.detail.payment')}</h2>
            <p className="mt-3 text-ink">{t(`orders.detail.paymentMethod.${order.paymentMethod}`)}</p>
          </section>
        </div>
      </div>

      {/* Whether an order can still be cancelled is decided by the server. */}
      {order.cancellable && (
        <section className="mt-12 border-t border-line pt-8">
          <p className="text-sm text-ink-soft">{t('orders.detail.cancelHint')}</p>
          <Button variant="secondary" className="mt-4" onClick={() => setConfirming(true)}>
            {t('orders.detail.cancel')}
          </Button>
        </section>
      )}

      <Dialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t('orders.detail.cancelConfirmTitle')}
        description={t('orders.detail.cancelConfirmText')}
      >
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={() => setConfirming(false)}>
            {t('orders.detail.keep')}
          </Button>
          <Button
            className="bg-danger hover:bg-danger/90"
            disabled={cancel.isPending}
            onClick={() => cancel.mutate(order.orderNumber, { onSettled: () => setConfirming(false) })}
          >
            {t('orders.detail.cancelConfirm')}
          </Button>
        </div>
      </Dialog>
    </>
  );
}
