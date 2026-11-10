import { Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Price } from '@/components/ui/Price';
import { QuantityStepper } from '@/components/ui/QuantityStepper';
import { ProductImage } from '@/features/catalog';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { useRemoveCartItem, useUpdateCartItem, type CartLine } from '../api/cart';

interface CartLineItemProps {
  line: CartLine;
  /** Compact layout without quantity controls (cart drawer). */
  compact?: boolean;
  onNavigate?: () => void;
}

/** One cart line. Prices and the allowed quantity come from the server. */
export function CartLineItem({ line, compact = false, onNavigate }: CartLineItemProps) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const update = useUpdateCartItem();
  const remove = useRemoveCartItem();
  const busy = update.isPending || remove.isPending;

  return (
    <li className={cn('flex gap-4 py-5', !line.available && 'opacity-70')}>
      <Link
        to={`/products/${line.productSlug}`}
        onClick={onNavigate}
        tabIndex={-1}
        aria-hidden
        className={cn(
          'block shrink-0 overflow-hidden rounded-xl bg-mist',
          compact ? 'size-20' : 'size-24 sm:size-28',
        )}
      >
        <ProductImage
          image={line.image}
          alt=""
          categorySlug={line.categorySlug}
          accentColor={line.colourHex}
          sizes="112px"
        />
      </Link>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <Link
              to={`/products/${line.productSlug}`}
              onClick={onNavigate}
              className="font-display text-lg leading-snug underline-offset-4 hover:underline"
            >
              {line.productName}
            </Link>
            {line.speciesName && <p className="truncate scientific text-sm text-muted">{line.speciesName}</p>}
            <p className="mt-1 text-sm text-ink-soft">
              {line.variantName}
              {compact && ` × ${line.quantity}`}
              {line.gradeName && ` · ${t('product.grade', { grade: line.gradeName })}`}
            </p>
          </div>
          <Price
            value={line.lineTotal}
            className={cn('shrink-0 font-medium', !line.available && 'line-through')}
          />
        </div>

        {!line.available && (
          <p className="mt-2 text-sm font-medium text-warning">{t('cart.line.unavailable')}</p>
        )}

        {!compact && (
          <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2">
            {line.available && (
              <QuantityStepper
                value={line.quantity}
                max={line.maxQuantity}
                disabled={busy}
                label={t('cart.quantity.labelFor', { name: line.productName })}
                onChange={(quantity) => update.mutate({ itemId: line.id, quantity })}
              />
            )}
            {line.available && (
              <span className="text-sm text-muted">
                {t('cart.line.each', { price: formatMoney(line.unitPrice, locale) })}
              </span>
            )}
            <button
              type="button"
              disabled={busy}
              onClick={() => remove.mutate(line.id)}
              aria-label={t('cart.line.removeLabel', { name: line.productName })}
              className="ml-auto inline-flex min-h-11 items-center gap-1.5 rounded-full px-3 text-sm text-ink-soft transition-colors hover:bg-danger-soft hover:text-danger disabled:opacity-50"
            >
              <Trash2 aria-hidden className="size-4" />
              {t('cart.line.remove')}
            </button>
          </div>
        )}
        {!compact && line.available && line.quantity >= line.maxQuantity && (
          <p className="mt-2 text-xs text-muted">{t('cart.quantity.max', { count: line.maxQuantity })}</p>
        )}
      </div>
    </li>
  );
}
