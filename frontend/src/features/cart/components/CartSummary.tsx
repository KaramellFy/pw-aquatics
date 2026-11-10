import { useTranslation } from 'react-i18next';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Price } from '@/components/ui/Price';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import type { Cart } from '../api/cart';

/** Cart summary. All amounts are the server's; shipping is added at checkout. */
export function CartSummary({ cart }: { cart: Cart }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <section
      aria-labelledby="cart-summary"
      className="rounded-card border border-brand-100 bg-brand-50 p-6 sm:p-8"
    >
      <h2 id="cart-summary" className="text-title">
        {t('cart.summary.title')}
      </h2>
      <dl className="mt-6 space-y-3 text-[0.9375rem]">
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">{t('cart.summary.subtotal')}</dt>
          <dd>
            <Price value={cart.totals.subtotal} />
          </dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">{t('cart.summary.shipping')}</dt>
          <dd className="text-muted">{t('cart.summary.shippingNote')}</dd>
        </div>
        <div className="flex items-baseline justify-between gap-4 border-t border-brand-200 pt-4">
          <dt className="font-medium">{t('cart.summary.total')}</dt>
          <dd className="text-right">
            <Price value={cart.totals.total} className="font-display text-2xl" />
            <span className="block text-xs text-muted">
              {t('cart.summary.includedTax', { amount: formatMoney(cart.totals.includedTax, locale) })}
            </span>
          </dd>
        </div>
      </dl>

      {/* The checkout needs an account; signed-out visitors are sent to the login page first. */}
      {cart.itemCount > 0 ? (
        <ButtonLink to="/checkout" size="lg" className="mt-8 w-full">
          {t('cart.summary.checkout')}
        </ButtonLink>
      ) : (
        <Button size="lg" className="mt-8 w-full" disabled>
          {t('cart.summary.checkout')}
        </Button>
      )}
      <ButtonLink to="/shop" variant="secondary" className="mt-4 w-full">
        {t('cart.summary.continue')}
      </ButtonLink>
    </section>
  );
}
