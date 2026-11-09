import { CalendarDays, Package, Thermometer } from 'lucide-react';
import { useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { Breadcrumbs } from '@/components/ui/Breadcrumbs';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { Price } from '@/components/ui/Price';
import { StatusPage } from '@/app/pages/StatusPage';
import { ApiError } from '@/lib/api/client';
import { toDecimalString } from '@/lib/format/money';
import { useIntlLocale } from '@/lib/i18n';
import { JsonLd } from '@/lib/seo/jsonLd';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useProduct, useRelatedProducts, type ProductDetail } from '../api/catalog';
import { AvailabilityBadge } from '../components/AvailabilityBadge';
import { CareTable } from '../components/CareTable';
import { ColourDots } from '../components/ColourDots';
import { GradeBadge } from '../components/GradeBadge';
import { ProductGrid } from '../components/ProductGrid';
import { ProductImage } from '../components/ProductImage';
import { VariantSelector } from '../components/VariantSelector';

const SCHEMA_AVAILABILITY: Record<ProductDetail['availability'], string> = {
  IN_STOCK: 'https://schema.org/InStock',
  LOW_STOCK: 'https://schema.org/LimitedAvailability',
  COMING_SOON: 'https://schema.org/PreOrder',
  OUT_OF_STOCK: 'https://schema.org/OutOfStock',
};

export function ProductPage() {
  const { slug = '' } = useParams();
  const { t } = useTranslation();
  const product = useProduct(slug);

  if (product.isPending) {
    return <ProductSkeleton />;
  }
  if (product.isError) {
    const notFound = product.error instanceof ApiError && product.error.status === 404;
    return (
      <>
        <PageMeta title={`${t('product.notFound.title')} – ${t('common.brand')}`} noIndex />
        <StatusPage
          eyebrow={notFound ? t('notFound.eyebrow') : t('common.brand')}
          title={notFound ? t('product.notFound.title') : t('error.title')}
          text={notFound ? t('product.notFound.text') : t('shop.error.text')}
          motif="shrimp"
          actions={
            notFound ? (
              <ButtonLink to="/shop">{t('product.notFound.cta')}</ButtonLink>
            ) : (
              <Button onClick={() => void product.refetch()}>{t('shop.error.retry')}</Button>
            )
          }
        />
      </>
    );
  }
  // Re-mount per product so the selected pack size resets when navigating between products.
  return <ProductView key={product.data.id} product={product.data} />;
}

function ProductView({ product }: { product: ProductDetail }) {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const related = useRelatedProducts(product.slug);
  const firstAvailable = product.variants.find((variant) => variant.available) ?? product.variants[0];
  const [variantId, setVariantId] = useState(firstAvailable?.id);
  const variant = product.variants.find((candidate) => candidate.id === variantId) ?? firstAvailable;
  const [primaryImage, ...moreImages] = product.images;
  // availableFrom is a calendar date (YYYY-MM-DD); format it in UTC so it never shifts by a day.
  const dateFormat = new Intl.DateTimeFormat(locale, { dateStyle: 'long', timeZone: 'UTC' });

  const prices = product.variants.map((v) => v.price);
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.species ? `${product.name} (${product.species.scientificName})` : product.name,
    description: product.seo.description ?? product.shortDescription ?? undefined,
    sku: product.variants[0]?.sku,
    category: product.categoryPath.map((c) => c.name).join(' > '),
    image: product.images.map((image) => image.url),
    offers: prices.length
      ? {
          '@type': 'AggregateOffer',
          priceCurrency: prices[0]?.currency,
          lowPrice: toDecimalString(prices.reduce((a, b) => (a.amount <= b.amount ? a : b))),
          highPrice: toDecimalString(prices.reduce((a, b) => (a.amount >= b.amount ? a : b))),
          offerCount: prices.length,
          availability: SCHEMA_AVAILABILITY[product.availability],
        }
      : undefined,
  };

  return (
    <>
      <PageMeta
        title={`${product.seo.title} – ${t('common.brand')}`}
        description={product.seo.description ?? undefined}
      />
      <JsonLd data={jsonLd} />

      <Container className="pt-10 pb-20 sm:pt-14">
        <Breadcrumbs
          label={t('nav.shop')}
          items={[
            { label: t('nav.shop'), to: '/shop' },
            ...product.categoryPath.map((c) => ({ label: c.name, to: `/shop/${c.slug}` })),
            { label: product.name },
          ]}
        />

        <div className="mt-8 grid gap-12 lg:grid-cols-12 lg:gap-16">
          {/* Gallery */}
          <section aria-label={t('product.gallery')} className="lg:col-span-7">
            <div className="lg:sticky lg:top-28">
              <div className="aspect-[4/5] overflow-hidden rounded-card bg-mist sm:aspect-[5/4] lg:aspect-[4/5]">
                <ProductImage
                  image={primaryImage ?? null}
                  alt={primaryImage?.alt ?? product.name}
                  categorySlug={product.categoryPath[0]?.slug}
                  accentColor={product.colours[0]?.hexCode}
                  sizes="(min-width: 1024px) 55vw, 100vw"
                  priority
                />
              </div>
              {moreImages.length > 0 && (
                <ul className="mt-4 grid grid-cols-4 gap-3">
                  {moreImages.map((image) => (
                    <li key={image.url} className="aspect-square overflow-hidden rounded-xl bg-mist">
                      <ProductImage image={image} alt={image.alt} sizes="12vw" />
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>

          {/* Purchase panel */}
          <div className="lg:col-span-5">
            <div className="flex flex-wrap items-center gap-3">
              {product.grade && <GradeBadge name={product.grade.name} />}
              <AvailabilityBadge availability={product.availability} />
            </div>
            <h1 className="mt-4 text-headline">{product.name}</h1>
            {product.species && (
              <p className="mt-2 text-lg text-muted">
                <span className="scientific">{product.species.scientificName}</span>
                {product.species.commonName && <> · {product.species.commonName}</>}
              </p>
            )}
            {product.shortDescription && (
              <p className="mt-6 text-lg leading-relaxed text-ink-soft">{product.shortDescription}</p>
            )}

            <div className="mt-8 border-t border-line pt-8">
              {variant && (
                <div className="flex items-baseline gap-3">
                  <Price
                    value={variant.price}
                    compareAt={variant.compareAtPrice}
                    className="font-display text-3xl"
                  />
                </div>
              )}
              <p className="mt-1 text-sm text-muted">
                <Trans
                  i18nKey="product.priceNote"
                  components={{
                    shippingLink: <Link to="/legal/shipping" className="underline underline-offset-4" />,
                  }}
                />
              </p>
              {product.availability === 'COMING_SOON' && product.availableFrom && (
                <p className="mt-3 text-sm font-medium text-brand-700">
                  {t('product.expectedFrom', { date: dateFormat.format(new Date(product.availableFrom)) })}
                </p>
              )}
            </div>

            {product.variants.length > 0 && (
              <div className="mt-8">
                <VariantSelector variants={product.variants} value={variant?.id} onChange={setVariantId} />
              </div>
            )}

            <div className="mt-8 space-y-3">
              {/* The cart arrives in Phase 4; until then the button is visibly disabled with an explanation. */}
              <Button size="lg" className="w-full" disabled aria-describedby="cart-soon">
                {t('product.addToCart')}
              </Button>
              <p id="cart-soon" className="text-sm text-muted">
                {t('product.cartSoon')}{' '}
                <Link to="/contact" className="font-medium text-brand-700 underline-offset-4 hover:underline">
                  {t('product.reserve')}
                </Link>
              </p>
            </div>

            {product.colours.length > 0 && (
              <div className="mt-8 flex items-center gap-3 text-sm text-muted">
                {t('product.colours')}
                <ColourDots colours={product.colours} />
                <span className="text-ink-soft">{product.colours.map((c) => c.name).join(', ')}</span>
              </div>
            )}

            {product.liveAnimal && (
              <ul className="mt-8 space-y-3 rounded-2xl border border-brand-100 bg-brand-50 p-5 text-sm text-ink-soft">
                <li className="flex gap-3">
                  <CalendarDays aria-hidden className="size-5 shrink-0 text-brand-700" strokeWidth={1.5} />
                  {t('product.shipping.days')}
                </li>
                <li className="flex gap-3">
                  <Package aria-hidden className="size-5 shrink-0 text-brand-700" strokeWidth={1.5} />
                  {t('product.shipping.packaging')}
                </li>
                <li className="flex gap-3">
                  <Thermometer aria-hidden className="size-5 shrink-0 text-brand-700" strokeWidth={1.5} />
                  {t('product.shipping.acclimate')}
                </li>
              </ul>
            )}
          </div>
        </div>

        {/* Description and care */}
        <div className="mt-20 grid gap-12 border-t border-line pt-16 lg:grid-cols-12 lg:gap-16">
          {product.descriptionHtml && (
            <section className="lg:col-span-7">
              <h2 className="text-title">{t('product.description')}</h2>
              {/* HTML is rendered from Markdown and sanitised on the server. */}
              <div
                className="rich-text mt-6 text-lg"
                dangerouslySetInnerHTML={{ __html: product.descriptionHtml }}
              />
              {product.careNotesHtml && (
                <div className="rich-text mt-6" dangerouslySetInnerHTML={{ __html: product.careNotesHtml }} />
              )}
            </section>
          )}
          {product.species && (
            <section className="lg:col-span-5">
              <h2 className="text-title">{t('product.care')}</h2>
              <CareTable species={product.species} className="mt-6" />
              <Link
                to={`/species/${product.species.slug}`}
                className="mt-4 inline-block text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
              >
                {t('product.speciesLink')}:{' '}
                <span className="scientific">{product.species.scientificName}</span>
              </Link>
            </section>
          )}
        </div>

        {related.data && related.data.length > 0 && (
          <section className="mt-24 border-t border-line pt-16">
            <h2 className="text-title">{t('product.related')}</h2>
            <ProductGrid products={related.data} columns={4} className="mt-10" />
          </section>
        )}
      </Container>
    </>
  );
}

function ProductSkeleton() {
  return (
    <Container className="animate-pulse pt-10 pb-20 sm:pt-14" aria-hidden>
      <div className="h-4 w-48 rounded bg-mist" />
      <div className="mt-8 grid gap-12 lg:grid-cols-12 lg:gap-16">
        <div className="aspect-[4/5] rounded-card bg-mist lg:col-span-7" />
        <div className="space-y-4 lg:col-span-5">
          <div className="h-6 w-32 rounded bg-mist" />
          <div className="h-12 w-3/4 rounded bg-mist" />
          <div className="h-5 w-1/2 rounded bg-mist" />
          <div className="h-24 w-full rounded bg-mist" />
        </div>
      </div>
    </Container>
  );
}
