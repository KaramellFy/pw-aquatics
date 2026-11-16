import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Price } from '@/components/ui/Price';
import { OrderTotalsList, type OrderTotals } from '@/features/account';
import { ProductImage } from '@/features/catalog';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import type { Quote, QuoteLine } from '../api/checkout';

/** The fields of a line shown in the summary; cart lines and quote lines both fit. */
export type SummaryLine = Pick<
  QuoteLine,
  | 'variantId'
  | 'productName'
  | 'variantName'
  | 'categorySlug'
  | 'colourHex'
  | 'image'
  | 'quantity'
  | 'lineTotal'
>;

type CouponState = NonNullable<Quote['coupon']>;

interface CheckoutSummaryProps {
  lines: SummaryLine[];
  /** Goods value from the cart, shown until a quote with shipping exists. */
  subtotal: OrderTotals['subtotal'];
  /** The cart's coupon discount, shown until a quote exists. */
  discount: OrderTotals['discount'];
  /** The quote's totals, once address and shipping method are chosen. */
  totals: OrderTotals | undefined;
  /** The coupon from the cart (as the quote or the cart sees it), or null. */
  coupon: CouponState | null | undefined;
}

/** What is being ordered and what it costs — every amount comes from the server. */
export function CheckoutSummary({ lines, subtotal, discount, totals, coupon }: CheckoutSummaryProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <section
      aria-labelledby="checkout-summary"
      className="rounded-card border border-brand-100 bg-brand-50 p-6 sm:p-8"
    >
      <h2 id="checkout-summary" className="text-title">
        {t('checkout.summary.title')}
      </h2>
      <ul className="mt-5 divide-y divide-brand-200 border-y border-brand-200">
        {lines.map((line) => (
          <li key={line.variantId} className="flex gap-4 py-4">
            <div className="size-16 shrink-0 overflow-hidden rounded-xl bg-surface">
              <ProductImage
                image={line.image}
                alt=""
                categorySlug={line.categorySlug}
                accentColor={line.colourHex}
                sizes="64px"
              />
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-display leading-snug">{line.productName}</p>
              <p className="text-sm text-ink-soft">{line.variantName}</p>
              <p className="text-sm text-ink-soft">
                {t('checkout.summary.quantity', { count: line.quantity })}
              </p>
            </div>
            <Price value={line.lineTotal} className="shrink-0 text-[0.9375rem]" />
          </li>
        ))}
      </ul>
      {totals ? (
        <OrderTotalsList totals={totals} couponCode={coupon?.applied ? coupon.code : null} className="mt-6" />
      ) : (
        <dl className="mt-6 space-y-3 text-[0.9375rem]">
          <div className="flex justify-between gap-4">
            <dt className="text-ink-soft">{t('orders.totals.subtotal')}</dt>
            <dd>
              <Price value={subtotal} />
            </dd>
          </div>
          {coupon?.applied && discount.amount > 0 && (
            <div className="flex justify-between gap-4">
              <dt className="text-ink-soft">{t('orders.totals.coupon', { code: coupon.code })}</dt>
              <dd className="text-success tabular-nums">
                {t('orders.totals.minus', { amount: formatMoney(discount, locale) })}
              </dd>
            </div>
          )}
          <div className="flex justify-between gap-4">
            <dt className="text-ink-soft">{t('orders.totals.shipping')}</dt>
            <dd className="text-muted">{t('checkout.summary.shippingPending')}</dd>
          </div>
        </dl>
      )}
      {coupon && !coupon.applied && (
        <Alert tone="info" className="mt-6">
          {t('checkout.summary.couponNotApplied', { code: coupon.code })}{' '}
          {t(`cart.coupon.problems.${coupon.problem ?? 'COUPON_INVALID'}`, {
            amount: coupon.minOrder ? formatMoney(coupon.minOrder, locale) : '',
            defaultValue: t('cart.coupon.problems.COUPON_INVALID'),
          })}
        </Alert>
      )}
    </section>
  );
}
