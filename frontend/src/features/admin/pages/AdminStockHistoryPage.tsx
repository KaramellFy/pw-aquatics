import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Pagination } from '@/components/ui/Pagination';
import { formatInstantDateTime } from '@/lib/format/date';
import { useIntlLocale } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { useStockMovements } from '../api/inventory';
import { useAdminProduct } from '../api/products';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { DataTable, Td, Th } from '../components/DataTable';
import { LoadState } from '../components/LoadState';
import { StockDialog } from '../components/StockDialog';

/** Every stock change of one product, newest first. */
export function AdminStockHistoryPage() {
  const { productId = '' } = useParams();
  const { t } = useTranslation();
  const locale = useIntlLocale();
  const [page, setPage] = useState(0);
  const [changing, setChanging] = useState(false);
  const product = useAdminProduct(productId);
  const movements = useStockMovements(productId, page);

  return (
    <>
      <AdminPageHeader
        title={
          product.data
            ? `${t('admin.inventory.historyPage.title')}: ${product.data.name}`
            : t('admin.inventory.historyPage.title')
        }
        lead={
          product.data ? t('admin.inventory.dialog.current', { count: product.data.stockUnits }) : undefined
        }
        back={{ to: '/admin/inventory', label: t('admin.inventory.historyPage.back') }}
        actions={
          product.data && (
            <Button onClick={() => setChanging(true)}>{t('admin.product.publish.manageStock')}</Button>
          )
        }
      />
      <LoadState
        isPending={movements.isPending}
        isError={movements.isError}
        hasData={Boolean(movements.data)}
        onRetry={() => void movements.refetch()}
      />
      {movements.data && movements.data.items.length === 0 && (
        <p className="rounded-card border border-line bg-surface px-6 py-12 text-center text-ink-soft">
          {t('admin.inventory.historyPage.empty')}
        </p>
      )}
      {movements.data && movements.data.items.length > 0 && (
        <>
          <DataTable
            caption={t('admin.inventory.historyPage.title')}
            head={
              <tr>
                <Th>{t('admin.inventory.historyPage.columns.date')}</Th>
                <Th>{t('admin.inventory.historyPage.columns.reason')}</Th>
                <Th className="text-right">{t('admin.inventory.historyPage.columns.change')}</Th>
                <Th className="text-right">{t('admin.inventory.historyPage.columns.after')}</Th>
                <Th>{t('admin.inventory.historyPage.columns.note')}</Th>
              </tr>
            }
          >
            {movements.data.items.map((movement) => (
              <tr key={movement.id}>
                <Td className="text-sm whitespace-nowrap text-ink-soft">
                  {formatInstantDateTime(movement.createdAt, locale)}
                </Td>
                <Td>{t(`admin.inventory.historyPage.reasons.${movement.reason}`)}</Td>
                <Td
                  className={cn(
                    'text-right font-medium tabular-nums',
                    movement.delta < 0 ? 'text-danger' : 'text-success',
                  )}
                >
                  {movement.delta > 0 ? `+${movement.delta}` : `−${Math.abs(movement.delta)}`}
                </Td>
                <Td className="text-right tabular-nums">{movement.stockAfter}</Td>
                <Td className="text-sm text-ink-soft">{movement.note ?? t('admin.common.none')}</Td>
              </tr>
            ))}
          </DataTable>
          <div className="mt-6">
            <Pagination
              page={movements.data.page + 1}
              totalPages={movements.data.totalPages}
              onChange={(next) => setPage(next - 1)}
            />
          </div>
        </>
      )}
      <StockDialog
        product={changing && product.data ? product.data : null}
        onClose={() => {
          setChanging(false);
          void product.refetch();
        }}
      />
    </>
  );
}
