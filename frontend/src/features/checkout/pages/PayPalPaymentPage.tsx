import type { PayPalNamespace } from '@paypal/paypal-js';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useCapturePayPalPayment, usePayPalPayment, type PayPalPayment } from '../api/payments';
import { PayPalButton, type ApprovalOutcome } from '../components/PayPalButton';
import { FormPlaceholder, PaymentFrame, PaymentSummary } from '../components/PaymentFrame';
import { getPayPal } from '../lib/paypal';

/**
 * Paying an order with PayPal. The amount, the deadline and the PayPal order come from the API.
 * After the customer approved in PayPal's window, our server takes the money and says whether
 * the order is paid; this page then moves on to the confirmation page.
 */
export function PayPalPaymentPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useTranslation();
  const payment = usePayPalPayment(orderNumber);
  const errorMessage = useApiErrorMessage();
  const orderLink = `/account/orders/${orderNumber}`;
  const completePage = `/checkout/complete/${orderNumber}`;

  let content;
  if (payment.isPending) {
    content = <FormPlaceholder label={t('checkout.pay.paypal.loading')} />;
  } else if (payment.isError) {
    const error = payment.error;
    if (error instanceof ApiError && error.code === 'ORDER_ALREADY_PAID') {
      return <Navigate to={completePage} replace />;
    }
    const notFound = error instanceof ApiError && error.status === 404;
    const final =
      notFound ||
      (error instanceof ApiError &&
        (error.code === 'ORDER_NOT_PAYABLE' || error.code === 'PAYMENT_METHOD_UNAVAILABLE'));
    content = (
      <Alert tone="error">
        <p>{notFound ? t('orders.detail.notFoundText') : errorMessage(error)}</p>
        <div className="mt-4 flex flex-wrap gap-3">
          {!final && (
            <Button variant="secondary" size="sm" onClick={() => void payment.refetch()}>
              {t('shop.error.retry')}
            </Button>
          )}
          <ButtonLink to={notFound ? '/account/orders' : orderLink} variant="secondary" size="sm">
            {t('checkout.pay.viewOrder')}
          </ButtonLink>
        </div>
      </Alert>
    );
  } else if (payment.data.alreadyPaid || !payment.data.paypalOrderId) {
    // PayPal has the money (or is checking it); the confirmation page shows how it stands.
    return <Navigate to={completePage} replace />;
  } else {
    content = <PayPalCheckout payment={payment.data} paypalOrderId={payment.data.paypalOrderId} />;
  }

  return (
    <PaymentFrame
      orderNumber={orderNumber}
      secure={t('checkout.pay.paypal.secure')}
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

function PayPalCheckout({ payment, paypalOrderId }: { payment: PayPalPayment; paypalOrderId: string }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const capture = useCapturePayPalPayment();
  const errorMessage = useApiErrorMessage();
  const [paypal, setPayPal] = useState<PayPalNamespace | null | undefined>(undefined);
  const [message, setMessage] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const completePage = `/checkout/complete/${payment.orderNumber}`;

  useEffect(() => {
    let active = true;
    getPayPal(payment.clientId, payment.amount.currency, i18n.language).then(
      (namespace) => active && setPayPal(namespace ?? null),
      () => active && setPayPal(null),
    );
    return () => {
      active = false;
    };
    // The language is fixed for the life of this page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [payment.clientId, payment.amount.currency]);

  async function approve(): Promise<ApprovalOutcome> {
    setMessage(null);
    try {
      const result = await capture.mutateAsync(payment.orderNumber);
      if (result.status === 'DECLINED') {
        setMessage(t('checkout.pay.paypal.declined'));
        return 'retry';
      }
      // Paid, or PayPal still checks it: the confirmation page shows how it stands.
      await navigate(completePage, { replace: true });
      return 'done';
    } catch (error) {
      if (error instanceof ApiError && error.code === 'ORDER_ALREADY_PAID') {
        await navigate(completePage, { replace: true });
      } else {
        setMessage(errorMessage(error));
      }
      return 'done';
    }
  }

  if (paypal === null || failed) {
    return (
      <Alert tone="error">
        <p>{t('checkout.pay.paypal.loadFailed')}</p>
        <Button variant="secondary" size="sm" className="mt-4" onClick={() => window.location.reload()}>
          {t('shop.error.retry')}
        </Button>
      </Alert>
    );
  }
  if (paypal === undefined) {
    return <FormPlaceholder label={t('checkout.pay.paypal.loading')} />;
  }
  return (
    <div>
      {capture.isPending && (
        <p role="status" className="mb-6 text-ink-soft">
          {t('checkout.pay.paypal.confirming')}
        </p>
      )}
      <div
        aria-busy={capture.isPending}
        className={capture.isPending ? 'pointer-events-none opacity-60' : undefined}
      >
        <PayPalButton
          paypal={paypal}
          paypalOrderId={paypalOrderId}
          onApprove={approve}
          onCancel={() => setMessage(t('checkout.pay.paypal.cancelled'))}
          onError={() => (capture.isPending ? setMessage(t('checkout.pay.paypal.failed')) : setFailed(true))}
        />
      </div>
      {message && (
        <Alert tone="info" className="mt-6">
          {message}
        </Alert>
      )}
    </div>
  );
}
