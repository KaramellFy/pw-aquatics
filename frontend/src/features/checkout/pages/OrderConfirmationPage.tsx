import { CircleCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { OrderTotalsList, PaymentInstructionsCard, useOrder } from '@/features/account';
import { formatCalendarDate } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';

/** "Thank you" page after an order: the order number, how to pay and what happens next. */
export function OrderConfirmationPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const order = useOrder(orderNumber);

  return (
    <>
      <PageMeta title={t('checkout.complete.metaTitle')} noIndex />
      <Container className="max-w-3xl pt-12 pb-24 sm:pt-16">
        <CircleCheck aria-hidden className="size-12 text-brand-700" strokeWidth={1.25} />
        <p className="mt-6 eyebrow text-success">{t('checkout.complete.eyebrow')}</p>
        <h1 className="mt-3 text-headline">{t('checkout.complete.title')}</h1>
        <p className="mt-4 text-lg text-ink-soft">{t('checkout.complete.text')}</p>
        <p className="mt-6 text-ink-soft">
          {t('checkout.complete.orderNumber')}:{' '}
          <strong className="font-medium text-ink tabular-nums">{orderNumber}</strong>
        </p>

        {order.isPending && (
          <p role="status" className="mt-10 text-muted">
            {t('common.loading')}
          </p>
        )}
        {order.isError && !order.data && (
          <Alert tone="error" className="mt-10">
            <p>{t('orders.loadError')}</p>
            <Button variant="secondary" size="sm" className="mt-4" onClick={() => void order.refetch()}>
              {t('shop.error.retry')}
            </Button>
          </Alert>
        )}

        {order.data && (
          <>
            {order.data.paymentInstructions && (
              <PaymentInstructionsCard
                instructions={order.data.paymentInstructions}
                dueAt={order.data.paymentDueAt}
                className="mt-10"
              />
            )}
            <section className="mt-10">
              <h2 className="text-title">{t('checkout.complete.next')}</h2>
              <ol className="mt-5 list-decimal space-y-3 pl-5 text-ink-soft marker:text-brand-700">
                {order.data.paymentInstructions && <li>{t('checkout.complete.stepPay')}</li>}
                <li>
                  {order.data.plannedDispatchDate
                    ? t('checkout.complete.stepShip', {
                        date: formatCalendarDate(order.data.plannedDispatchDate, locale, 'full'),
                      })
                    : t('checkout.complete.stepShipNoDate')}
                </li>
                <li>{t('checkout.complete.stepTrack')}</li>
              </ol>
            </section>
            <OrderTotalsList totals={order.data.totals} className="mt-10 border-t border-line pt-6" />
          </>
        )}

        <div className="mt-10 flex flex-col gap-3 sm:flex-row">
          <ButtonLink to={`/account/orders/${orderNumber}`}>{t('checkout.complete.viewOrder')}</ButtonLink>
          <ButtonLink to="/shop" variant="secondary">
            {t('checkout.complete.continue')}
          </ButtonLink>
        </div>
      </Container>
    </>
  );
}
