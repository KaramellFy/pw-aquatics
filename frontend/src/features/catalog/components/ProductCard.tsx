import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Price } from '@/components/ui/Price';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import type { ProductSummary } from '../api/catalog';
import { AvailabilityBadge } from './AvailabilityBadge';
import { ColourDots } from './ColourDots';
import { GradeBadge } from './GradeBadge';
import { ProductImage } from './ProductImage';

/** A product in the shop grid. Every value shown comes from the API. */
export function ProductCard({ product, priority = false }: { product: ProductSummary; priority?: boolean }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const purchasable = product.availability === 'IN_STOCK' || product.availability === 'LOW_STOCK';

  return (
    <article className="group relative">
      <Link to={`/products/${product.slug}`} className="block rounded-card">
        <div className="relative aspect-[4/5] overflow-hidden rounded-card bg-mist">
          <ProductImage
            image={product.primaryImage}
            hoverImage={product.secondaryImage}
            alt={product.primaryImage?.alt ?? product.name}
            categorySlug={product.category.slug}
            accentColor={product.colours[0]?.hexCode}
            sizes="(min-width: 1280px) 22vw, (min-width: 640px) 45vw, 92vw"
            priority={priority}
            className="transition-transform duration-(--duration-slow) ease-(--ease-out-soft) group-hover:scale-[1.03]"
          />
          {!purchasable && (
            <span className="absolute top-3 left-3 rounded-full bg-surface/90 px-3 py-1 text-xs font-medium text-ink shadow-soft backdrop-blur">
              {t(`product.availability.${product.availability}`)}
            </span>
          )}
        </div>

        <div className="mt-4 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="truncate font-display text-lg leading-snug font-normal">{product.name}</h3>
            {product.species && (
              <p className="mt-0.5 truncate scientific text-sm text-muted">
                {product.species.scientificName}
              </p>
            )}
          </div>
          {product.grade && <GradeBadge name={product.grade.name} className="mt-0.5" />}
        </div>

        <div className="mt-3 flex items-center justify-between gap-3">
          {product.priceFrom && (
            <span className="text-[0.9375rem] font-medium">
              {product.hasMultipleVariants ? (
                t('product.from', { price: formatMoney(product.priceFrom, locale) })
              ) : (
                <Price value={product.priceFrom} compareAt={product.compareAtPriceFrom} />
              )}
            </span>
          )}
          <ColourDots colours={product.colours} />
        </div>
        {purchasable && <AvailabilityBadge availability={product.availability} className="mt-2 text-xs" />}
      </Link>
    </article>
  );
}

export function ProductCardSkeleton() {
  return (
    <div aria-hidden className="animate-pulse">
      <div className="aspect-[4/5] rounded-card bg-mist" />
      <div className="mt-4 h-5 w-2/3 rounded bg-mist" />
      <div className="mt-2 h-4 w-1/2 rounded bg-mist" />
      <div className="mt-3 h-4 w-1/3 rounded bg-mist" />
    </div>
  );
}
