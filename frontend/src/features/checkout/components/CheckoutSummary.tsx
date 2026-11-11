import { useTranslation } from 'react-i18next';
import { Price } from '@/components/ui/Price';
import { OrderTotalsList, type OrderTotals } from '@/features/account';
import { ProductImage } from '@/features/catalog';
import type { QuoteLine } from '../api/checkout';

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

interface CheckoutSummaryProps {
  lines: SummaryLine[];
  /** Goods value from the cart, shown until a quote with shipping exists. */
  subtotal: OrderTotals['subtotal'];
  /** The quote's totals, once address and shipping method are chosen. */
  totals: OrderTotals | undefined;
}

/** What is being ordered and what it costs — every amount comes from the server. */
export function CheckoutSummary({ lines, subtotal, totals }: CheckoutSummaryProps) {
  const { t } = useTranslation();
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
        <OrderTotalsList totals={totals} className="mt-6" />
      ) : (
        <dl className="mt-6 space-y-3 text-[0.9375rem]">
          <div className="flex justify-between gap-4">
            <dt className="text-ink-soft">{t('orders.totals.subtotal')}</dt>
            <dd>
              <Price value={subtotal} />
            </dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-ink-soft">{t('orders.totals.shipping')}</dt>
            <dd className="text-muted">{t('checkout.summary.shippingPending')}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}
