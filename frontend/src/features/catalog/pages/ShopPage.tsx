import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Breadcrumbs } from '@/components/ui/Breadcrumbs';
import { Button } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { Pagination } from '@/components/ui/Pagination';
import { PageMeta } from '@/lib/seo/PageMeta';
import {
  useCategories,
  useColours,
  useFacets,
  useGrades,
  useProducts,
  useSpeciesList,
  type CategoryNode,
} from '../api/catalog';
import { ActiveFilters } from '../components/ActiveFilters';
import { FilterPanel } from '../components/FilterPanel';
import { MobileFilters } from '../components/MobileFilters';
import { ProductGrid } from '../components/ProductGrid';
import { ShopToolbar } from '../components/ShopToolbar';
import { activeFilterCount, toFacetQuery, toProductQuery, type ShopFilters } from '../model/shopFilters';
import { useShopFilters } from '../model/useShopFilters';

function findCategory(nodes: CategoryNode[] | undefined, slug: string | undefined): CategoryNode[] {
  for (const node of nodes ?? []) {
    if (node.slug === slug) return [node];
    const child = node.children.find((candidate) => candidate.slug === slug);
    if (child) return [node, child];
  }
  return [];
}

export function ShopPage() {
  const { t } = useTranslation();
  const { filters, update, clear } = useShopFilters();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const resultsRef = useRef<HTMLDivElement>(null);

  const productQuery = useMemo(() => toProductQuery(filters), [filters]);
  const facetQuery = useMemo(() => toFacetQuery(filters), [filters]);
  const products = useProducts(productQuery);
  const facets = useFacets(facetQuery);
  const categories = useCategories();
  const species = useSpeciesList();
  const colours = useColours();
  const grades = useGrades();

  const categoryPath = findCategory(categories.data, filters.category);
  const category = categoryPath.at(-1);
  const title = category?.name ?? t('shop.allAnimals');
  const total = products.data?.totalItems;

  const changePage = (page: number) => {
    update({ page });
    resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const panel = (
    <FilterPanel
      filters={filters}
      onChange={update}
      facets={facets.data}
      categories={categories.data}
      species={species.data}
      colours={colours.data}
      grades={grades.data}
    />
  );

  return (
    <>
      <PageMeta
        title={category ? `${category.name} – ${t('common.brand')}` : t('shop.meta.title')}
        description={category?.description ?? t('shop.meta.description')}
      />
      <Container className="pt-10 pb-24 sm:pt-14 lg:pb-32">
        <Breadcrumbs
          label={t('nav.shop')}
          items={[
            { label: t('nav.shop'), to: '/shop' },
            ...categoryPath.map((node) => ({ label: node.name, to: `/shop/${node.slug}` })),
          ]}
        />
        <header className="mt-6 max-w-3xl">
          <h1 className="text-headline">{title}</h1>
          <p className="mt-4 text-lg leading-relaxed text-ink-soft">
            {category?.description ?? t('shop.lead')}
          </p>
        </header>

        <div className="mt-12 grid gap-10 lg:grid-cols-12 lg:gap-12">
          <aside className="hidden lg:col-span-3 lg:block" aria-label={t('shop.filters.title')}>
            {/* Scrolls on its own when taller than the screen, so every filter stays reachable. */}
            <div className="sticky top-28 -mx-2 max-h-[calc(100dvh-8rem)] overflow-y-auto overscroll-contain px-2 pb-8">
              {panel}
            </div>
          </aside>

          <div ref={resultsRef} className="scroll-mt-28 lg:col-span-9">
            <ShopToolbar
              key={filters.q ?? ''}
              filters={filters}
              onChange={update}
              activeFilters={activeFilterCount(filters)}
              onOpenFilters={() => setFiltersOpen(true)}
            />
            <div className="mt-4 flex min-h-9 flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-muted" aria-live="polite">
                {total !== undefined && t('shop.results', { count: total })}
              </p>
            </div>
            <ActiveFilters
              filters={filters}
              onChange={update}
              onClear={clear}
              species={species.data}
              colours={colours.data}
              grades={grades.data}
            />

            <div className="mt-8">
              <Results
                filters={filters}
                state={products}
                onClear={clear}
                onRetry={() => void products.refetch()}
              />
            </div>

            {products.data && (
              <div className="mt-16">
                <Pagination page={filters.page} totalPages={products.data.totalPages} onChange={changePage} />
              </div>
            )}
          </div>
        </div>
      </Container>

      <MobileFilters open={filtersOpen} onOpenChange={setFiltersOpen} resultCount={total}>
        {panel}
      </MobileFilters>
    </>
  );
}

function Results({
  filters,
  state,
  onClear,
  onRetry,
}: {
  filters: ShopFilters;
  state: ReturnType<typeof useProducts>;
  onClear: () => void;
  onRetry: () => void;
}) {
  const { t } = useTranslation();

  if (state.isError && !state.data) {
    return (
      <Alert tone="error">
        <p>{t('shop.error.text')}</p>
        <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
          {t('shop.error.retry')}
        </Button>
      </Alert>
    );
  }
  if (state.data && state.data.items.length === 0) {
    return (
      <div className="rounded-card border border-line bg-mist px-6 py-16 text-center">
        <h2 className="text-title">{t('shop.empty.title')}</h2>
        <p className="mx-auto mt-3 max-w-md text-ink-soft">{t('shop.empty.text')}</p>
        {activeFilterCount(filters) > 0 && (
          <Button variant="secondary" className="mt-8" onClick={onClear}>
            {t('shop.empty.reset')}
          </Button>
        )}
      </div>
    );
  }
  return <ProductGrid products={state.data?.items} loading={state.isFetching} />;
}
