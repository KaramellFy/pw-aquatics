import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { Price } from '@/components/ui/Price';
import { cn } from '@/lib/cn';
import type { ProductDetail } from '../api/catalog';

type Variant = ProductDetail['variants'][number];

interface VariantSelectorProps {
  variants: Variant[];
  value: string | undefined;
  onChange: (variantId: string) => void;
}

/**
 * Pack-size choice as large radio cards. Availability per pack comes from the API. The
 * selection frame is one object that glides to the chosen card.
 */
export function VariantSelector({ variants, value, onChange }: VariantSelectorProps) {
  const { t } = useTranslation();
  const name = useId();
  const frame = useId();
  return (
    <fieldset>
      <legend className="mb-3 eyebrow text-ink">{t('product.packSize')}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        {variants.map((variant) => {
          const selected = variant.id === value;
          return (
            <label
              key={variant.id}
              className={cn(
                'relative flex min-h-16 press cursor-pointer items-center justify-between gap-3 rounded-xl border px-4 py-3 transition-[border-color]',
                'has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-brand-400/40',
                'border-line-strong',
                !selected && variant.available && 'hover:border-ink-soft',
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
              {selected && (
                <SlidingIndicator
                  layoutId={`pack-${frame}`}
                  className="-inset-px rounded-xl"
                  markClassName="border-2 border-brand-700 bg-brand-50"
                />
              )}
              {/* Above the frame, which passes over the other cards while it moves. */}
              <span className="relative z-10">
                <span className="block font-medium">{variant.name}</span>
                {!variant.available && (
                  <span className="block text-xs text-muted">{t('product.soldOut')}</span>
                )}
              </span>
              <Price
                value={variant.price}
                compareAt={variant.compareAtPrice}
                className="relative z-10 text-[0.9375rem]"
              />
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
