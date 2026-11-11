import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Price } from '@/components/ui/Price';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatCalendarDate } from '@/lib/format/date';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import type { ShippingOption, ShippingOptions } from '../api/checkout';
import { OptionCard } from './OptionCard';

interface ShippingStepProps {
  options: ShippingOptions | undefined;
  loading: boolean;
  error: unknown;
  /** Display name of the destination country. */
  countryName: string;
  selected: ShippingOption | undefined;
  onSelect: (code: string) => void;
  onRetry: () => void;
  onContinue: () => void;
}

/** Step 2: choose how the parcel travels. Prices and dates are the server's. */
export function ShippingStep({
  options,
  loading,
  error,
  countryName,
  selected,
  onSelect,
  onRetry,
  onContinue,
}: ShippingStepProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const errorMessage = useApiErrorMessage();

  if (error && !options) {
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
  if (loading || !options) {
    return (
      <p role="status" className="text-muted">
        {t('common.loading')}
      </p>
    );
  }
  if (options.methods.length === 0) {
    return (
      <Alert tone="info">
        {options.paused ? (
          <>
            <p>{t('checkout.shipping.paused')}</p>
            {options.pauseMessage && <p className="mt-1">{options.pauseMessage}</p>}
          </>
        ) : (
          <p>{t('checkout.shipping.unavailable', { country: countryName })}</p>
        )}
      </Alert>
    );
  }

  return (
    <>
      {options.paused && options.pausedUntil && (
        <Alert tone="info" className="mb-5">
          <p>
            {t('checkout.shipping.pausedUntil', { date: formatCalendarDate(options.pausedUntil, locale) })}
          </p>
          {options.pauseMessage && <p className="mt-1">{options.pauseMessage}</p>}
        </Alert>
      )}
      <fieldset>
        <legend className="sr-only">{t('checkout.shipping.legend')}</legend>
        <div className="space-y-3">
          {options.methods.map((method) => (
            <OptionCard
              key={method.code}
              name="shipping-method"
              value={method.code}
              checked={method.code === selected?.code}
              onChange={() => onSelect(method.code)}
            >
              <span className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <span className="font-medium text-ink">{method.name}</span>
                <span className="font-medium text-ink">
                  {method.price.amount === 0 ? t('checkout.shipping.free') : <Price value={method.price} />}
                </span>
              </span>
              {method.description && (
                <span className="mt-1 block text-sm text-ink-soft">{method.description}</span>
              )}
              <ShippingDates method={method} className="mt-2 block text-sm text-ink-soft" />
              {method.freeFrom && method.price.amount > 0 && (
                <span className="mt-1 block text-sm text-muted">
                  {t('checkout.shipping.freeFrom', { amount: formatMoney(method.freeFrom, locale) })}
                </span>
              )}
            </OptionCard>
          ))}
        </div>
      </fieldset>
      <p className="mt-4 text-sm text-muted">{t('checkout.shipping.liveNote')}</p>
      <div className="mt-8">
        <Button size="lg" disabled={!selected} onClick={onContinue}>
          {t('checkout.continue')}
        </Button>
      </div>
    </>
  );
}

/** Earliest dispatch and expected delivery of a shipping method, as given by the server. */
export function ShippingDates({ method, className }: { method: ShippingOption; className?: string }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const { estimatedDeliveryFrom: from, estimatedDeliveryTo: to } = method;
  return (
    <span className={className}>
      {t('checkout.shipping.dispatch', {
        date: formatCalendarDate(method.plannedDispatchDate, locale, 'full'),
      })}
      {from && (
        <span className="block">
          {to && to !== from
            ? t('checkout.shipping.deliveryRange', {
                from: formatCalendarDate(from, locale, 'medium'),
                to: formatCalendarDate(to, locale, 'medium'),
              })
            : t('checkout.shipping.deliveryOn', { date: formatCalendarDate(from, locale, 'full') })}
        </span>
      )}
    </span>
  );
}
