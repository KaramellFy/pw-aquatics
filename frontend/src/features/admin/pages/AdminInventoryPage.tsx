import { History } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Pagination } from '@/components/ui/Pagination';
import { cn } from '@/lib/cn';
import { useAdminProducts, type AdminProductSummary, type StockFilter } from '../api/products';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { DataTable, Td, Th } from '../components/DataTable';
import { LoadState } from '../components/LoadState';
import { ProductThumb } from '../components/ProductThumb';
import { StatusBadge } from '../components/StatusBadge';
import { StockDialog } from '../components/StockDialog';

const FILTERS: StockFilter[] = ['ALL', 'LOW_STOCK', 'OUT_OF_STOCK'];

/** Stock per product, lowest first, with quick changes and a link to each history. */
export function AdminInventoryPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const filter = (params.get('stock') as StockFilter | null) ?? 'ALL';
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? '0');
  const [search, setSearch] = useState(q);
  const [changing, setChanging] = useState<AdminProductSummary | null>(null);
  const products = useAdminProducts({ q, stock: filter, sort: 'STOCK_ASC', page });

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  return (
    <>
      <AdminPageHeader title={t('admin.inventory.title')} lead={t('admin.inventory.lead')} />

      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div role="group" aria-label={t('admin.inventory.title')} className="flex flex-wrap gap-2">
          {FILTERS.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={filter === value}
              onClick={() => update({ stock: value === 'ALL' ? undefined : value })}
              className={cn(
                'min-h-11 rounded-full border px-4 text-sm font-medium',
                filter === value
                  ? 'border-ink bg-ink text-white'
                  : 'border-line-strong bg-surface text-ink-soft hover:border-ink-soft',
              )}
            >
              {t(`admin.inventory.filters.${value}`)}
            </button>
          ))}
        </div>
        <form
          role="search"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            update({ q: search.trim() || undefined });
          }}
          className="flex gap-2"
        >
          <label htmlFor="inventory-search" className="sr-only">
            {t('admin.common.search')}
          </label>
          <input
            id="inventory-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={t('admin.products.searchPlaceholder')}
            maxLength={100}
            className="block h-11 w-full rounded-full border border-line-strong bg-surface px-4 focus:border-brand-700 focus:ring-4 focus:ring-brand-400/35 focus:outline-none md:w-72"
          />
          <Button type="submit" variant="secondary">
            {t('admin.common.search')}
          </Button>
        </form>
      </div>

      <LoadState
        isPending={products.isPending}
        isError={products.isError}
        hasData={Boolean(products.data)}
        onRetry={() => void products.refetch()}
      />
      {products.data && products.data.items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t('admin.inventory.empty')}
        </p>
      )}
      {products.data && products.data.items.length > 0 && (
        <>
          <DataTable
            caption={t('admin.inventory.title')}
            head={
              <tr>
                <Th>{t('admin.inventory.columns.product')}</Th>
                <Th className="text-right">{t('admin.inventory.columns.stock')}</Th>
                <Th className="text-right">{t('admin.inventory.columns.threshold')}</Th>
                <Th>{t('admin.inventory.columns.shop')}</Th>
                <Th>
                  <span className="sr-only">{t('admin.common.actions')}</span>
                </Th>
              </tr>
            }
          >
            {products.data.items.map((product) => (
              <tr key={product.id}>
                <Td>
                  <div className="flex items-center gap-3">
                    <ProductThumb image={product.image} />
                    <div className="min-w-0">
                      <Link
                        to={`/admin/products/${product.id}`}
                        className="font-medium underline-offset-4 hover:underline"
                      >
                        {product.name}
                      </Link>
                      <div className="mt-1">
                        <StatusBadge status={product.status} />
                      </div>
                    </div>
                  </div>
                </Td>
                <Td
                  className={cn(
                    'text-right text-lg tabular-nums',
                    product.stockUnits === 0 && 'text-danger',
                    product.stockUnits > 0 &&
                      product.stockUnits <= product.lowStockThreshold &&
                      'text-brand-800',
                  )}
                >
                  {product.stockUnits}
                </Td>
                <Td className="text-right text-ink-soft tabular-nums">{product.lowStockThreshold}</Td>
                <Td className="text-sm text-ink-soft">{t(`admin.availability.${product.availability}`)}</Td>
                <Td>
                  <div className="flex justify-end gap-1">
                    <Button
                      variant="secondary"
                      size="sm"
                      className="min-h-11"
                      aria-label={t('admin.inventory.changeLabel', { name: product.name })}
                      onClick={() => setChanging(product)}
                    >
                      {t('admin.inventory.change')}
                    </Button>
                    <Link
                      to={`/admin/inventory/${product.id}`}
                      aria-label={t('admin.inventory.historyLabel', { name: product.name })}
                      className="inline-flex min-h-11 items-center gap-2 rounded-full px-4 text-sm text-ink-soft hover:bg-ink/5"
                    >
                      <History aria-hidden className="size-4" />
                      {t('admin.inventory.history')}
                    </Link>
                  </div>
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

      <StockDialog product={changing} onClose={() => setChanging(null)} />
    </>
  );
}
