import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Container } from '@/components/ui/Container';
import { ProductGrid, useProducts } from '@/features/catalog';
import { FEATURED_QUERY } from './Hero';

/**
 * Featured animals from the API, minus the one already shown in the hero. Hidden if nothing
 * else is featured or the API is unavailable.
 */
export function FeaturedProducts() {
  const { t } = useTranslation();
  const products = useProducts(FEATURED_QUERY);
  const items = products.data?.items.slice(1);

  if (products.isError || (items && items.length === 0)) {
    return null;
  }
  return (
    <section className="bg-mist py-20 sm:py-28">
      <Container>
        <div className="flex flex-wrap items-baseline justify-between gap-4">
          <h2 className="text-headline">{t('home.featured.title')}</h2>
          <Link
            to="/shop"
            className="group inline-flex items-center gap-2 text-[0.9375rem] font-medium text-brand-700"
          >
            {t('home.featured.cta')}
            <ArrowRight
              aria-hidden
              className="size-4 transition-transform duration-(--duration-base) group-hover:translate-x-0.5"
            />
          </Link>
        </div>
        <ProductGrid
          products={items}
          loading={products.isFetching}
          columns={3}
          skeletons={3}
          className="mt-12"
        />
      </Container>
    </section>
  );
}
