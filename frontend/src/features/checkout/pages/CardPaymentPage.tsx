import { Elements } from '@stripe/react-stripe-js';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { useOrder, type PostalAddress } from '@/features/account';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useCardPayment, type CardPayment } from '../api/payments';
import { CardPaymentForm, LoadFailed } from '../components/CardPaymentForm';
import { FormPlaceholder, PaymentFrame, PaymentSummary } from '../components/PaymentFrame';
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
    <PaymentFrame
      orderNumber={orderNumber}
      secure={t('checkout.pay.secure')}
      summary={
        payment.data && (
          <PaymentSummary
            orderNumber={payment.data.orderNumber}
            amount={payment.data.amount}
            paymentDueAt={payment.data.paymentDueAt}
          />
        )
      }
    >
      {content}
    </PaymentFrame>
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
