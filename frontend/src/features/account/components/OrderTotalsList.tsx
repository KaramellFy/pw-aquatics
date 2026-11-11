import { useTranslation } from 'react-i18next';
import { Price } from '@/components/ui/Price';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import type { OrderTotals } from '../api/orders';

/** Subtotal, shipping and total exactly as the server calculated them. */
export function OrderTotalsList({ totals, className }: { totals: OrderTotals; className?: string }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <dl className={cn('space-y-3 text-[0.9375rem]', className)}>
      <div className="flex justify-between gap-4">
        <dt className="text-ink-soft">{t('orders.totals.subtotal')}</dt>
        <dd>
          <Price value={totals.subtotal} />
        </dd>
      </div>
      <div className="flex justify-between gap-4">
        <dt className="text-ink-soft">{t('orders.totals.shipping')}</dt>
        <dd>{totals.shipping.amount === 0 ? t('orders.totals.free') : <Price value={totals.shipping} />}</dd>
      </div>
      <div className="flex items-baseline justify-between gap-4 border-t border-line pt-4">
        <dt className="font-medium">{t('orders.totals.total')}</dt>
        <dd className="text-right">
          <Price value={totals.total} className="font-display text-2xl" />
          <span className="block text-xs text-muted">
            {t('orders.totals.includedTax', { amount: formatMoney(totals.includedTax, locale) })}
          </span>
        </dd>
      </div>
    </dl>
  );
}
