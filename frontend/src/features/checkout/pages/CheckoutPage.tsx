import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { AddressBlock, useAddresses } from '@/features/account';
import { useSession } from '@/features/auth';
import { useCart } from '@/features/cart';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useQuote, useShippingMethods } from '../api/checkout';
import { AddressStep } from '../components/AddressStep';
import { CheckoutStep, type StepState } from '../components/CheckoutStep';
import { CheckoutSummary } from '../components/CheckoutSummary';
import { ReviewStep } from '../components/ReviewStep';
import { ShippingDates, ShippingStep } from '../components/ShippingStep';

const STEPS = ['address', 'shipping', 'review'] as const;
type Step = (typeof STEPS)[number];

/**
 * Checkout in three steps: address → shipping → review and order. The page only collects
 * choices; shipping options, prices, totals and dates are all asked from the server.
 */
export function CheckoutPage() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const cart = useCart();
  const addresses = useAddresses();

  const [step, setStep] = useState<Step>('address');
  const [shippingAddressId, setShippingAddressId] = useState<string>();
  const [billingAddressId, setBillingAddressId] = useState<string>();
  const [billingSame, setBillingSame] = useState(true);
  const [methodCode, setMethodCode] = useState<string>();

  // Until the customer chooses, the defaults from the address book are preselected.
  const list = addresses.data ?? [];
  const shippingAddress =
    list.find((address) => address.id === shippingAddressId) ??
    list.find((address) => address.defaultShipping) ??
    list[0];
  const billingAddress =
    list.find((address) => address.id === billingAddressId) ??
    list.find((address) => address.defaultBilling) ??
    list[0];

  // Right after signing in, the guest cart is still being merged into the account's cart.
  const cartSettled = cart.isSuccess && !cart.isFetching;
  const hasItems = (cart.data?.itemCount ?? 0) > 0;

  const shipping = useShippingMethods(shippingAddress?.countryCode, cartSettled && hasItems);
  const methods = shipping.data?.methods ?? [];
  const method = methods.find((option) => option.code === methodCode) ?? methods[0];
  const quote = useQuote(shippingAddress?.id, method?.code, step === 'review' && cartSettled && hasItems);

  // Moving to another step puts the keyboard and screen-reader focus on its heading.
  const headings = useRef<Partial<Record<Step, HTMLHeadingElement | null>>>({});
  const previousStep = useRef(step);
  useEffect(() => {
    if (previousStep.current !== step) {
      previousStep.current = step;
      headings.current[step]?.focus();
    }
  }, [step]);

  const stateOf = (candidate: Step): StepState => {
    const position = STEPS.indexOf(candidate) - STEPS.indexOf(step);
    return position === 0 ? 'current' : position < 0 ? 'done' : 'upcoming';
  };
  const countryName = shippingAddress
    ? (new Intl.DisplayNames([i18n.resolvedLanguage ?? 'en'], { type: 'region' }).of(
        shippingAddress.countryCode,
      ) ?? shippingAddress.countryCode)
    : '';

  let content;
  if (cart.isPending || addresses.isPending) {
    content = (
      <p role="status" className="mt-10 text-muted">
        {t('common.loading')}
      </p>
    );
  } else if ((cart.isError && !cart.data) || (addresses.isError && !addresses.data)) {
    content = (
      <Alert tone="error" className="mt-10">
        <p>{t('checkout.loadError')}</p>
        <Button
          variant="secondary"
          size="sm"
          className="mt-4"
          onClick={() => {
            void cart.refetch();
            void addresses.refetch();
          }}
        >
          {t('shop.error.retry')}
        </Button>
      </Alert>
    );
  } else if (cartSettled && !hasItems) {
    content = (
      <div className="mt-10 rounded-card border border-line bg-mist px-6 py-16 text-center">
        <h2 className="text-title">{t('checkout.empty.title')}</h2>
        <p className="mx-auto mt-3 max-w-md text-ink-soft">{t('checkout.empty.text')}</p>
        <ButtonLink to="/shop" className="mt-8">
          {t('checkout.empty.cta')}
        </ButtonLink>
      </div>
    );
  } else if (cart.data) {
    const user = session.data?.user;
    content = (
      <div className="mt-10 grid gap-10 lg:grid-cols-12 lg:gap-12">
        <div className="space-y-4 lg:col-span-7">
          <CheckoutStep
            number={1}
            total={STEPS.length}
            title={t('checkout.steps.address')}
            state={stateOf('address')}
            onEdit={() => setStep('address')}
            headingRef={(node) => {
              headings.current.address = node;
            }}
            summary={shippingAddress && <AddressBlock address={shippingAddress} className="text-sm" />}
          >
            <AddressStep
              addresses={list}
              shippingAddress={shippingAddress}
              billingAddress={billingAddress}
              billingSame={billingSame}
              defaultNames={{ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '' }}
              onShippingChange={setShippingAddressId}
              onBillingChange={setBillingAddressId}
              onBillingSameChange={setBillingSame}
              onContinue={() => setStep('shipping')}
            />
          </CheckoutStep>

          <CheckoutStep
            number={2}
            total={STEPS.length}
            title={t('checkout.steps.shipping')}
            state={stateOf('shipping')}
            onEdit={() => setStep('shipping')}
            headingRef={(node) => {
              headings.current.shipping = node;
            }}
            summary={
              method && (
                <p className="text-sm">
                  <span className="block text-ink">{method.name}</span>
                  <ShippingDates method={method} />
                </p>
              )
            }
          >
            <ShippingStep
              options={shipping.data}
              loading={shipping.isPending || !cartSettled}
              error={shipping.error}
              countryName={countryName}
              selected={method}
              onSelect={setMethodCode}
              onRetry={() => void shipping.refetch()}
              onContinue={() => setStep('review')}
            />
          </CheckoutStep>

          <CheckoutStep
            number={3}
            total={STEPS.length}
            title={t('checkout.steps.review')}
            state={stateOf('review')}
            headingRef={(node) => {
              headings.current.review = node;
            }}
          >
            {shippingAddress && (
              <ReviewStep
                quote={quote.data}
                loading={quote.isPending}
                error={quote.error}
                shippingAddress={shippingAddress}
                billingAddress={billingSame ? undefined : billingAddress}
                onRetry={() => void quote.refetch()}
              />
            )}
          </CheckoutStep>
        </div>

        <div className="lg:col-span-5">
          <div className="lg:sticky lg:top-28">
            <CheckoutSummary
              lines={quote.data?.items ?? cart.data.items.filter((line) => line.available)}
              subtotal={cart.data.totals.subtotal}
              totals={step === 'review' ? quote.data?.totals : undefined}
            />
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      <PageMeta title={t('checkout.metaTitle')} noIndex />
      <Container className="pt-10 pb-24 sm:pt-14">
        <h1 className="text-headline">{t('checkout.title')}</h1>
        {content}
      </Container>
    </>
  );
}
