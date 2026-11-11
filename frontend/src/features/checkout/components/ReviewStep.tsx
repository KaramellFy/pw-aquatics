import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { CheckboxField, TextAreaField } from '@/components/ui/Field';
import { AddressBlock, type Address } from '@/features/account';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { usePlaceOrder, type Quote } from '../api/checkout';
import { ShippingDates } from './ShippingStep';

/** Errors after which the customer has to look at the cart again. */
const CART_ERRORS = ['CART_CHANGED', 'CART_EMPTY', 'INSUFFICIENT_STOCK', 'PRODUCT_UNAVAILABLE'];
const FIELDS = ['customerNote', 'acceptedTerms', 'acceptedLiveAnimalConditions'] as const;
const LEGAL_LINK = 'text-brand-700 underline underline-offset-4';

/** Mirrors the server's rules for instant feedback; the server checks them again. */
function reviewSchema(t: TFunction, liveAnimals: boolean) {
  return z.object({
    customerNote: z.string().trim().max(1000, t('auth.validation.tooLong')),
    acceptedTerms: z.boolean().refine((accepted) => accepted, t('checkout.review.termsRequired')),
    acceptedLiveAnimalConditions: z
      .boolean()
      .refine((accepted) => accepted || !liveAnimals, t('checkout.review.liveAnimalsRequired')),
  });
}
type ReviewValues = z.infer<ReturnType<typeof reviewSchema>>;

interface ReviewStepProps {
  quote: Quote | undefined;
  loading: boolean;
  error: unknown;
  shippingAddress: Address;
  /** Undefined when the order is billed to the delivery address. */
  billingAddress: Address | undefined;
  onRetry: () => void;
}

/**
 * Step 3: everything at a glance, the mandatory confirmations and the order button.
 * The order sends ids, the shipping method and the total the customer has seen — no prices.
 */
export function ReviewStep({
  quote,
  loading,
  error,
  shippingAddress,
  billingAddress,
  onRetry,
}: ReviewStepProps) {
  const { t, i18n } = useTranslation();
  const place = usePlaceOrder();
  const errorMessage = useApiErrorMessage();
  const liveAnimals = quote?.containsLiveAnimals ?? false;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => reviewSchema(t, liveAnimals), [t, i18n.language, liveAnimals]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ReviewValues>({
    resolver: zodResolver(schema),
    defaultValues: { customerNote: '', acceptedTerms: false, acceptedLiveAnimalConditions: false },
  });

  if (error && !quote) {
    return (
      <Alert tone="error">
        <p>{errorMessage(error)}</p>
        <div className="mt-4 flex flex-wrap gap-3">
          <Button variant="secondary" size="sm" onClick={onRetry}>
            {t('shop.error.retry')}
          </Button>
          <ButtonLink to="/cart" variant="secondary" size="sm">
            {t('checkout.reviewCart')}
          </ButtonLink>
        </div>
      </Alert>
    );
  }
  if (loading || !quote) {
    return (
      <p role="status" className="text-muted">
        {t('common.loading')}
      </p>
    );
  }

  const paymentMethod = quote.paymentMethods[0];
  const onSubmit = handleSubmit((values) => {
    if (!paymentMethod) return;
    place.mutate(
      {
        shippingAddressId: shippingAddress.id,
        billingAddressId: billingAddress?.id,
        shippingMethodCode: quote.shippingMethod.code,
        paymentMethod,
        customerNote: values.customerNote || undefined,
        acceptedTerms: values.acceptedTerms,
        acceptedLiveAnimalConditions: values.acceptedLiveAnimalConditions,
        // The total as shown on this page; the server refuses the order if its own total differs.
        expectedTotal: quote.totals.total,
      },
      { onError: (placeError) => applyServerFieldErrors(placeError, setError, FIELDS) },
    );
  });
  const cartProblem = place.error instanceof ApiError && CART_ERRORS.includes(place.error.code);

  return (
    <form onSubmit={onSubmit} noValidate>
      <dl className="grid gap-6 sm:grid-cols-2">
        <div>
          <dt className="font-sans eyebrow text-brand-700">{t('checkout.review.deliverTo')}</dt>
          <dd className="mt-3">
            <AddressBlock address={shippingAddress} className="text-sm" />
          </dd>
        </div>
        <div>
          <dt className="font-sans eyebrow text-brand-700">{t('checkout.review.billTo')}</dt>
          <dd className="mt-3">
            {billingAddress ? (
              <AddressBlock address={billingAddress} className="text-sm" />
            ) : (
              <p className="text-sm text-ink-soft">{t('checkout.review.sameAsDelivery')}</p>
            )}
          </dd>
        </div>
        <div>
          <dt className="font-sans eyebrow text-brand-700">{t('checkout.steps.shipping')}</dt>
          <dd className="mt-3 text-sm">
            <span className="block text-ink">{quote.shippingMethod.name}</span>
            <ShippingDates method={quote.shippingMethod} className="mt-1 block text-ink-soft" />
          </dd>
        </div>
        <div>
          <dt className="font-sans eyebrow text-brand-700">{t('checkout.review.payment')}</dt>
          <dd className="mt-3 text-sm">
            {paymentMethod ? (
              <>
                <span className="block text-ink">{t(`orders.detail.paymentMethod.${paymentMethod}`)}</span>
                {paymentMethod === 'BANK_TRANSFER' && (
                  <span className="mt-1 block text-ink-soft">{t('checkout.review.bankTransferText')}</span>
                )}
              </>
            ) : (
              <span className="text-danger">{t('checkout.review.noPayment')}</span>
            )}
          </dd>
        </div>
      </dl>

      <TextAreaField
        className="mt-8"
        rows={3}
        maxLength={1000}
        label={t('checkout.review.note')}
        hint={t('checkout.review.noteHint')}
        error={errors.customerNote?.message}
        {...register('customerNote')}
      />

      <div className="mt-8 space-y-4">
        <CheckboxField
          label={
            <Trans
              i18nKey="checkout.review.terms"
              components={{
                terms: <Link to="/legal/terms" target="_blank" className={LEGAL_LINK} />,
                withdrawal: <Link to="/legal/withdrawal" target="_blank" className={LEGAL_LINK} />,
              }}
            />
          }
          error={errors.acceptedTerms?.message}
          {...register('acceptedTerms')}
        />
        {liveAnimals && (
          <CheckboxField
            label={
              <Trans
                i18nKey="checkout.review.liveAnimals"
                components={{
                  shipping: <Link to="/legal/shipping" target="_blank" className={LEGAL_LINK} />,
                }}
              />
            }
            error={errors.acceptedLiveAnimalConditions?.message}
            {...register('acceptedLiveAnimalConditions')}
          />
        )}
      </div>

      {place.isError && (
        <Alert tone="error" className="mt-8">
          <p>{errorMessage(place.error)}</p>
          {cartProblem && (
            <ButtonLink to="/cart" variant="secondary" size="sm" className="mt-4">
              {t('checkout.reviewCart')}
            </ButtonLink>
          )}
        </Alert>
      )}

      <Button
        type="submit"
        size="lg"
        className="mt-8 w-full sm:w-auto"
        disabled={place.isPending || !paymentMethod}
      >
        {place.isPending ? t('checkout.review.submitting') : t('checkout.review.submit')}
      </Button>
    </form>
  );
}
