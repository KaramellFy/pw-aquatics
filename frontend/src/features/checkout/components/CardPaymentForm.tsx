import { PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import type { StripePaymentElementOptions } from '@stripe/stripe-js';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { queryKeys } from '@/lib/api/queryKeys';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import type { PostalAddress } from '@/features/account';
import type { Money } from '@/lib/api/types';

interface CardPaymentFormProps {
  orderNumber: string;
  /** The order total from the API, shown on the button; Stripe charges the PaymentIntent's amount. */
  amount: Money;
  /** The order's billing address: its country (and postcode) are filled in for the customer. */
  billingAddress?: PostalAddress;
}

/**
 * Stripe's Payment Element and the pay button. Card details are typed into Stripe's frames;
 * this form only asks Stripe to confirm the payment. If the bank wants the customer to
 * confirm (3-D Secure), Stripe shows that step, or sends them to the bank and back to the
 * confirmation page.
 */
export function CardPaymentForm({ orderNumber, amount, billingAddress }: CardPaymentFormProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const stripe = useStripe();
  const elements = useElements();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [ready, setReady] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const confirmationPage = `/checkout/complete/${orderNumber}`;

  async function pay(event: FormEvent) {
    event.preventDefault();
    if (!stripe || !elements || !ready || busy) return;
    setBusy(true);
    setMessage(null);
    const result = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: new URL(confirmationPage, window.location.origin).href },
      redirect: 'if_required',
    });
    if (result.error) {
      // Stripe explains declined cards in the customer's language; form mistakes are shown in the form itself.
      if (result.error.type === 'card_error') {
        setMessage(result.error.message ?? t('checkout.pay.failed'));
      } else if (result.error.type !== 'validation_error') {
        setMessage(t('checkout.pay.failed'));
      }
      setBusy(false);
      return;
    }
    // Paid or being processed: the confirmation page waits for our server to hear it from Stripe.
    void queryClient.invalidateQueries({ queryKey: queryKeys.allOrders });
    await navigate(confirmationPage, { replace: true });
  }

  if (loadFailed) {
    return <LoadFailed />;
  }

  return (
    <form onSubmit={(event) => void pay(event)} noValidate>
      <div className="relative min-h-64">
        {!ready && (
          <p role="status" className="absolute inset-0 flex items-center justify-center text-muted">
            {t('checkout.pay.loading')}
          </p>
        )}
        <PaymentElement
          options={paymentElementOptions(billingAddress)}
          onReady={() => setReady(true)}
          onLoadError={() => setLoadFailed(true)}
        />
      </div>

      {message && (
        <Alert tone="error" className="mt-6">
          {message}
        </Alert>
      )}

      <Button type="submit" size="lg" className="mt-8 w-full" aria-busy={busy} aria-disabled={!ready || busy}>
        {busy ? t('checkout.pay.paying') : t('checkout.pay.submit', { amount: formatMoney(amount, locale) })}
      </Button>
    </form>
  );
}

/**
 * Only the fields Stripe asks for are filled in (country, and the postcode where cards need
 * one). Stripe's "Link" (saving card details in a Stripe account, with email and phone number)
 * is switched off: customers already have an account with us, and it would hand more personal
 * data to Stripe than a payment needs.
 */
function paymentElementOptions(billingAddress: PostalAddress | undefined): StripePaymentElementOptions {
  return {
    wallets: { link: 'never' },
    defaultValues: billingAddress && {
      billingDetails: {
        address: { country: billingAddress.countryCode, postal_code: billingAddress.postalCode },
      },
    },
  };
}

/** Stripe's script or form could not be loaded (no connection, or a content blocker). */
export function LoadFailed() {
  const { t } = useTranslation();
  return (
    <Alert tone="error">
      <p>{t('checkout.pay.loadFailed')}</p>
      <Button variant="secondary" size="sm" className="mt-4" onClick={() => window.location.reload()}>
        {t('shop.error.retry')}
      </Button>
    </Alert>
  );
}
