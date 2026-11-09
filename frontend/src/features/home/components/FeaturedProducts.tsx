import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { Section, SectionHeading } from '@/components/ui/Section';
import { ProductGrid, useProducts } from '@/features/catalog';

const FEATURED_QUERY = { featured: 'true', availability: 'IN_STOCK', size: '4' };

/** Featured animals from the API. Hidden if nothing is featured or the API is unavailable. */
export function FeaturedProducts() {
  const { t } = useTranslation();
  const products = useProducts(FEATURED_QUERY);

  if (products.isError || (products.data && products.data.items.length === 0)) {
    return null;
  }
  return (
    <Section tone="mist">
      <div className="flex flex-col gap-8 sm:flex-row sm:items-end sm:justify-between">
        <SectionHeading eyebrow={t('home.featured.eyebrow')} title={t('home.featured.title')} />
        <ButtonLink to="/shop" variant="secondary" className="group self-start sm:self-auto">
          {t('home.featured.cta')}
          <ArrowRight aria-hidden className="size-4 transition-transform group-hover:translate-x-0.5" />
        </ButtonLink>
      </div>
      <ProductGrid
        products={products.data?.items}
        loading={products.isFetching}
        columns={4}
        skeletons={4}
        className="mt-16"
      />
    </Section>
  );
}
