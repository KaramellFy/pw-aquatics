import { Elements } from '@stripe/react-stripe-js';
import { LockKeyhole } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { useOrder, type PostalAddress } from '@/features/account';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatLocalTime } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useCardPayment, type CardPayment } from '../api/payments';
import { CardPaymentForm, LoadFailed } from '../components/CardPaymentForm';
import { getStripe, stripeAppearance, stripeLocale } from '../lib/stripe';

/**
 * Paying an order by card (or Apple Pay / Google Pay) with Stripe's Payment Element. The
 * amount, the deadline and the client secret come from the API; whether the order is paid is
 * decided by the server when Stripe confirms it, so this page only hands over to Stripe and
 * then moves on to the confirmation page.
 */
export function CardPaymentPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const payment = useCardPayment(orderNumber);
  // For the billing country; the form also works without it.
  const order = useOrder(orderNumber);
  const errorMessage = useApiErrorMessage();
  const orderLink = `/account/orders/${orderNumber}`;

  let content;
  if (payment.isPending || (order.isPending && !order.isError)) {
    content = <FormPlaceholder label={t('checkout.pay.loading')} />;
  } else if (payment.isError) {
    const error = payment.error;
    if (error instanceof ApiError && error.code === 'ORDER_ALREADY_PAID') {
      return <Navigate to={`/checkout/complete/${orderNumber}`} replace />;
    }
    const final =
      error instanceof ApiError &&
      (error.code === 'ORDER_NOT_PAYABLE' ||
        error.code === 'PAYMENT_METHOD_UNAVAILABLE' ||
        error.status === 404);
    content = (
      <Alert tone="error">
        <p>
          {error instanceof ApiError && error.status === 404
            ? t('orders.detail.notFoundText')
            : errorMessage(error)}
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          {!final && (
            <Button variant="secondary" size="sm" onClick={() => void payment.refetch()}>
              {t('shop.error.retry')}
            </Button>
          )}
          <ButtonLink
            to={final && error.status === 404 ? '/account/orders' : orderLink}
            variant="secondary"
            size="sm"
          >
            {t('checkout.pay.viewOrder')}
          </ButtonLink>
        </div>
      </Alert>
    );
  } else if (payment.data.alreadyPaid || !payment.data.clientSecret) {
    // Stripe has the money; the order is marked as paid as soon as Stripe tells our server.
    return <Navigate to={`/checkout/complete/${orderNumber}`} replace />;
  } else {
    content = (
      <PaymentForm
        payment={payment.data}
        clientSecret={payment.data.clientSecret}
        billingAddress={order.data?.billingAddress}
      />
    );
  }

  return (
    <>
      <PageMeta title={t('checkout.pay.metaTitle')} noIndex />
      <Container className="max-w-2xl pt-12 pb-24 sm:pt-16">
        <p className="eyebrow text-brand-700">{t('checkout.pay.eyebrow')}</p>
        <h1 className="mt-3 text-headline">{t('checkout.pay.title')}</h1>
        {payment.data && <Summary payment={payment.data} />}
        <div className="mt-8">{content}</div>
        <p className="mt-8 flex gap-3 text-sm text-ink-soft">
          <LockKeyhole aria-hidden className="mt-0.5 size-4 shrink-0 text-brand-700" />
          <span>{t('checkout.pay.secure')}</span>
        </p>
        <p className="mt-3 text-sm text-ink-soft">
          {t('checkout.pay.later')}{' '}
          <ButtonLink to={orderLink} variant="link" className="text-sm">
            {t('checkout.pay.viewOrder')}
          </ButtonLink>
        </p>
      </Container>
    </>
  );
}

function Summary({ payment }: { payment: CardPayment }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <div className="mt-4 space-y-1 text-lg text-ink-soft">
      <p>
        {t('checkout.pay.order', { number: payment.orderNumber })} ·{' '}
        <strong className="font-medium text-ink tabular-nums">{formatMoney(payment.amount, locale)}</strong>
      </p>
      {payment.paymentDueAt && (
        <p className="text-base">
          {t('checkout.pay.deadline', { time: formatLocalTime(payment.paymentDueAt, locale) })}
        </p>
      )}
    </div>
  );
}

interface PaymentFormProps {
  payment: CardPayment;
  clientSecret: string;
  billingAddress: PostalAddress | undefined;
}

function PaymentForm({ payment, clientSecret, billingAddress }: PaymentFormProps) {
  const { i18n } = useTranslation();
  const [loadFailed, setLoadFailed] = useState(false);
  const stripe = useMemo(
    () =>
      getStripe(payment.publishableKey).catch(() => {
        setLoadFailed(true);
        return null;
      }),
    [payment.publishableKey],
  );
  // Elements reads its options once; the language is fixed for the life of this form.
  const options = useMemo(
    () => ({ clientSecret, appearance: stripeAppearance(), locale: stripeLocale(i18n.language) }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [clientSecret],
  );
  if (loadFailed) {
    return <LoadFailed />;
  }
  return (
    <Elements stripe={stripe} options={options}>
      <CardPaymentForm
        orderNumber={payment.orderNumber}
        amount={payment.amount}
        billingAddress={billingAddress}
      />
    </Elements>
  );
}

/** Keeps the space of the payment form while it loads, so nothing jumps. */
export function FormPlaceholder({ label }: { label: string }) {
  return (
    <div className="flex min-h-64 items-center justify-center rounded-card border border-line bg-mist">
      <p role="status" className="text-muted">
        {label}
      </p>
    </div>
  );
}
