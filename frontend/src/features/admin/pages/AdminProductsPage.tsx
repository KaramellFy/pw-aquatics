import { Plus, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { ButtonLink } from '@/components/ui/Button';
import { SelectField } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Price } from '@/components/ui/Price';
import { formatInstantDateTime } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { useAdminProducts, type ProductSort, type ProductStatus } from '../api/products';
import { categoryTree, useAdminCategories } from '../api/taxonomy';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { DataTable, Td, Th } from '../components/DataTable';
import { LoadState } from '../components/LoadState';
import { ProductThumb } from '../components/ProductThumb';
import { StatusBadge } from '../components/StatusBadge';

const STATUSES: ProductStatus[] = ['DRAFT', 'ACTIVE', 'ARCHIVED'];
const SORTS: ProductSort[] = ['UPDATED_DESC', 'NAME_ASC', 'STOCK_ASC'];

/** All products with search and filters. Filters live in the URL, so "back" keeps them. */
export function AdminProductsPage() {
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const status = (params.get('status') as ProductStatus | null) ?? undefined;
  const categoryId = params.get('categoryId') ?? undefined;
  const sort = (params.get('sort') as ProductSort | null) ?? 'UPDATED_DESC';
  const page = Number(params.get('page') ?? '0');
  const [search, setSearch] = useState(q);

  const products = useAdminProducts({ q, status, categoryId, sort, page });
  const categories = useAdminCategories();

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };
  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    update({ q: search.trim() || undefined });
  };

  return (
    <>
      <AdminPageHeader
        title={t('admin.products.title')}
        lead={t('admin.products.lead')}
        actions={
          <ButtonLink to="/admin/products/new">
            <Plus aria-hidden className="size-4" />
            {t('admin.products.new')}
          </ButtonLink>
        }
      />

      <div className="mb-6 grid gap-4 md:grid-cols-[1fr_auto_auto_auto] md:items-end">
        <form role="search" onSubmit={onSearch} className="flex items-end gap-2">
          <div className="flex-1 space-y-2">
            <label htmlFor="admin-product-search" className="block text-sm font-medium text-ink-soft">
              {t('admin.common.search')}
            </label>
            <input
              id="admin-product-search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('admin.products.searchPlaceholder')}
              maxLength={100}
              className="block h-12 w-full rounded-xl border border-line-strong bg-surface px-4 text-base focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none"
            />
          </div>
          <button
            type="submit"
            aria-label={t('admin.common.search')}
            className="inline-flex size-12 items-center justify-center rounded-xl bg-ink text-white hover:bg-ink-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            <Search aria-hidden className="size-5" />
          </button>
        </form>
        <SelectField
          label={t('admin.products.status')}
          value={status ?? ''}
          onChange={(event) => update({ status: event.target.value || undefined })}
        >
          <option value="">{t('admin.products.anyStatus')}</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {t(`admin.status.${value}`)}
            </option>
          ))}
        </SelectField>
        <SelectField
          label={t('admin.products.category')}
          value={categoryId ?? ''}
          onChange={(event) => update({ categoryId: event.target.value || undefined })}
        >
          <option value="">{t('admin.products.anyCategory')}</option>
          {categoryTree(categories.data ?? []).map(({ category, depth }) => (
            <option key={category.id} value={category.id}>
              {' '.repeat(depth) + category.name}
            </option>
          ))}
        </SelectField>
        <SelectField
          label={t('admin.products.sort')}
          value={sort}
          onChange={(event) => update({ sort: event.target.value })}
        >
          {SORTS.map((value) => (
            <option key={value} value={value}>
              {t(`admin.products.sorts.${value}`)}
            </option>
          ))}
        </SelectField>
      </div>

      <LoadState
        isPending={products.isPending}
        isError={products.isError}
        hasData={Boolean(products.data)}
        onRetry={() => void products.refetch()}
      />

      {products.data && products.data.items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t('admin.products.empty')}
        </p>
      )}

      {products.data && products.data.items.length > 0 && (
        <>
          <DataTable
            caption={t('admin.products.title')}
            head={
              <tr>
                <Th>{t('admin.products.columns.product')}</Th>
                <Th>{t('admin.products.columns.status')}</Th>
                <Th className="text-right">{t('admin.products.columns.stock')}</Th>
                <Th className="text-right">{t('admin.products.columns.price')}</Th>
                <Th>{t('admin.products.columns.updated')}</Th>
              </tr>
            }
          >
            {products.data.items.map((product) => (
              <tr key={product.id} className="hover:bg-mist/60">
                <Td>
                  <div className="flex items-center gap-3">
                    <ProductThumb image={product.image} />
                    <div className="min-w-0">
                      <Link
                        to={`/admin/products/${product.id}`}
                        className="font-medium text-ink underline-offset-4 hover:underline"
                      >
                        {product.name}
                      </Link>
                      <p className="truncate text-sm text-muted">
                        {[product.categoryName, product.speciesName, product.gradeName]
                          .filter(Boolean)
                          .join(' · ')}
                      </p>
                    </div>
                  </div>
                </Td>
                <Td>
                  <StatusBadge status={product.status} />
                </Td>
                <Td className="text-right tabular-nums">
                  {t('admin.products.animals', { count: product.stockUnits })}
                </Td>
                <Td className="text-right">
                  {product.priceFrom ? (
                    <Price value={product.priceFrom} />
                  ) : (
                    <span className="text-sm text-muted">{t('admin.products.noPrice')}</span>
                  )}
                  <span className="block text-xs text-muted">
                    {t('admin.products.variants', { count: product.variantCount })}
                  </span>
                </Td>
                <Td className="text-sm whitespace-nowrap text-ink-soft">
                  {formatInstantDateTime(product.updatedAt, locale)}
                </Td>
              </tr>
            ))}
          </DataTable>
          <div className="mt-6">
            <Pagination
              page={products.data.page + 1}
              totalPages={products.data.totalPages}
              onChange={(next) => update({ page: String(next - 1) })}
            />
          </div>
        </>
      )}
    </>
  );
}
