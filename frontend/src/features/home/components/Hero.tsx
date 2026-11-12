import { ArrowRight } from 'lucide-react';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { ProductImage, useProducts, type ProductSummary } from '@/features/catalog';
import { formatMoney } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { MaskedWords } from '@/components/motion/MaskedWords';

/** Shared with FeaturedProducts, so both read the same cached request. */
export const FEATURED_QUERY = { featured: 'true', availability: 'IN_STOCK', size: '4' };

/**
 * Opening of the home page: what the shop sells and how it ships, in plain words, next to an
 * animal that is actually for sale right now.
 */
export function Hero() {
  const { t } = useTranslation();
  const featured = useProducts(FEATURED_QUERY).data?.items[0];

  return (
    <section className="border-b border-line">
      <Container className="grid gap-12 pt-10 pb-16 sm:pt-14 lg:grid-cols-12 lg:gap-10 lg:pt-20 lg:pb-24">
        <div className="flex flex-col justify-end lg:col-span-7">
          <h1 className="text-display text-balance">
            <MaskedWords text={t('home.hero.title')} />
          </h1>
          <p className="mt-8 max-w-xl animate-enter text-lg leading-relaxed text-pretty text-ink-soft [animation-delay:320ms]">
            <Trans
              i18nKey="home.hero.lead"
              components={{
                days: <strong className="font-medium text-ink" />,
              }}
            />
          </p>
          <div className="mt-10 flex animate-enter flex-wrap items-center gap-x-6 gap-y-4 [animation-delay:400ms]">
            <ButtonLink to="/shop" size="lg" className="group">
              {t('home.hero.primaryCta')}
              <ArrowRight
                aria-hidden
                className="size-4 transition-transform duration-(--duration-base) group-hover:translate-x-0.5"
              />
            </ButtonLink>
            <Link
              to="/legal/shipping"
              className="text-[0.9375rem] font-medium text-brand-700 underline decoration-brand-300 underline-offset-[6px] transition-colors hover:decoration-brand-700"
            >
              {t('home.hero.secondaryCta')}
            </Link>
          </div>
        </div>

        {featured && <Spotlight product={featured} />}
      </Container>
    </section>
  );
}

/** One animal from the featured list, opening with a wipe from the bottom. */
function Spotlight({ product }: { product: ProductSummary }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  return (
    <Link to={`/products/${product.slug}`} className="group block self-end lg:col-span-5 lg:col-start-8">
      <p className="mb-3 flex items-center gap-2 text-sm text-ink-soft">
        <span aria-hidden className="size-1.5 rounded-full bg-success" />
        {t('home.hero.spotlight')}
      </p>
      <div className="aspect-[5/4] animate-[wipe_900ms_var(--ease-out)_200ms_both] overflow-hidden rounded-card bg-mist">
        <ProductImage
          image={product.primaryImage}
          alt={product.primaryImage?.alt ?? product.name}
          categorySlug={product.category.slug}
          accentColor={product.colours[0]?.hexCode}
          sizes="(min-width: 1024px) 40vw, 100vw"
          priority
          className="transition-transform duration-(--duration-slow) ease-(--ease-out) group-hover:scale-[1.02]"
        />
      </div>
      <div className="mt-4 flex items-baseline justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate font-display text-xl">{product.name}</p>
          {product.species && (
            <p className="truncate scientific text-sm text-muted">{product.species.scientificName}</p>
          )}
        </div>
        {product.priceFrom && (
          <p className="shrink-0 text-[0.9375rem] font-medium">
            {product.hasMultipleVariants
              ? t('product.from', { price: formatMoney(product.priceFrom, locale) })
              : formatMoney(product.priceFrom, locale)}
          </p>
        )}
      </div>
    </Link>
  );
}
