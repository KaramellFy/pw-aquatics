import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';
import { Breadcrumbs } from '@/components/ui/Breadcrumbs';
import { ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { NotFoundPage } from '@/app/pages/NotFoundPage';
import { ApiError } from '@/lib/api/client';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useProducts, useSpecies } from '../api/catalog';
import { CareTable } from '../components/CareTable';
import { ProductGrid } from '../components/ProductGrid';

export function SpeciesPage() {
  const { slug = '' } = useParams();
  const { t } = useTranslation();
  const species = useSpecies(slug);
  const products = useProducts({ species: slug, size: '8' });

  if (species.isError && species.error instanceof ApiError && species.error.status === 404) {
    return <NotFoundPage />;
  }
  const data = species.data;
  const title = data ? (data.commonName ?? data.scientificName) : '';

  return (
    <>
      {data && (
        <PageMeta
          title={`${title} (${data.scientificName}) – ${t('common.brand')}`}
          description={data.descriptionHtml?.replace(/<[^>]+>/g, '').slice(0, 160)}
        />
      )}
      <Container className="pt-10 pb-24 sm:pt-14">
        <Breadcrumbs
          label={t('nav.shop')}
          items={[{ label: t('nav.shop'), to: '/shop' }, { label: title || '…' }]}
        />
        {data ? (
          <div className="mt-8 grid gap-12 lg:grid-cols-12 lg:gap-16">
            <header className="lg:col-span-7">
              <p className="eyebrow text-brand-700">{t('species.eyebrow')}</p>
              <h1 className="mt-4 text-headline">{title}</h1>
              <p className="mt-2 scientific text-xl text-muted">{data.scientificName}</p>
              {data.descriptionHtml && (
                // Sanitised on the server.
                <div
                  className="rich-text mt-8 text-lg"
                  dangerouslySetInnerHTML={{ __html: data.descriptionHtml }}
                />
              )}
            </header>
            <section className="lg:col-span-5">
              <h2 className="text-title">{t('species.care')}</h2>
              <CareTable species={data} className="mt-6" />
            </section>
          </div>
        ) : (
          <div aria-hidden className="mt-8 h-64 animate-pulse rounded-card bg-mist" />
        )}

        <section className="mt-24 border-t border-line pt-16">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
            <h2 className="text-title">{t('species.available')}</h2>
            <ButtonLink
              to={`/shop?species=${encodeURIComponent(slug)}`}
              variant="secondary"
              className="group self-start"
            >
              {t('species.viewAll')}
              <ArrowRight aria-hidden className="size-4 transition-transform group-hover:translate-x-0.5" />
            </ButtonLink>
          </div>
          {products.data?.items.length === 0 ? (
            <p className="mt-8 text-ink-soft">{t('species.none')}</p>
          ) : (
            <ProductGrid
              products={products.data?.items}
              loading={products.isFetching}
              columns={4}
              skeletons={4}
              className="mt-10"
            />
          )}
        </section>
      </Container>
    </>
  );
}
