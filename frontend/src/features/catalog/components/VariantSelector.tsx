import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Price } from '@/components/ui/Price';
import { cn } from '@/lib/cn';
import type { ProductDetail } from '../api/catalog';

type Variant = ProductDetail['variants'][number];

interface VariantSelectorProps {
  variants: Variant[];
  value: string | undefined;
  onChange: (variantId: string) => void;
}

/** Pack-size choice as large radio cards. Availability per pack comes from the API. */
export function VariantSelector({ variants, value, onChange }: VariantSelectorProps) {
  const { t } = useTranslation();
  const name = useId();
  return (
    <fieldset>
      <legend className="mb-3 eyebrow text-ink-soft">{t('product.packSize')}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        {variants.map((variant) => {
          const selected = variant.id === value;
          return (
            <label
              key={variant.id}
              className={cn(
                'relative flex min-h-16 cursor-pointer items-center justify-between gap-3 rounded-2xl border px-4 py-3 transition-[border-color,background-color,box-shadow]',
                'has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-brand-400/40',
                selected
                  ? 'border-brand-700 bg-brand-50 shadow-soft'
                  : 'border-line-strong hover:border-ink-soft',
                !variant.available && 'cursor-not-allowed opacity-60',
              )}
            >
              <input
                type="radio"
                name={name}
                value={variant.id}
                checked={selected}
                disabled={!variant.available}
                onChange={() => onChange(variant.id)}
                className="sr-only"
              />
              <span>
                <span className="block font-medium">{variant.name}</span>
                {!variant.available && (
                  <span className="block text-xs text-muted">{t('product.soldOut')}</span>
                )}
              </span>
              <Price value={variant.price} compareAt={variant.compareAtPrice} className="text-[0.9375rem]" />
              {selected && (
                <span aria-hidden className="absolute top-3 right-3 size-2 rounded-full bg-brand-400" />
              )}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
