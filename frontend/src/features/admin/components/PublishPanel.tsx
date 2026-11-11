import { CircleAlert, CircleCheck, Copy, ExternalLink } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import {
  useArchiveProduct,
  useDuplicateProduct,
  usePublishProduct,
  type AdminProduct,
} from '../api/products';
import { AdminCard } from './AdminCard';
import { ConfirmDialog } from './ConfirmDialog';
import { StatusBadge } from './StatusBadge';

/**
 * Publication status with what is still missing (as reported by the server) and the
 * publish, archive and duplicate actions.
 */
export function PublishPanel({ product }: { product: AdminProduct }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const publish = usePublishProduct(product.id);
  const archive = useArchiveProduct(product.id);
  const duplicate = useDuplicateProduct(product.id);
  const errorMessage = useApiErrorMessage();
  const [confirmArchive, setConfirmArchive] = useState(false);
  const error = publish.error ?? archive.error ?? duplicate.error;
  const problems = product.publishProblems;

  return (
    <AdminCard title={t('admin.product.publish.title')} id="product-status">
      <div className="flex items-center gap-2">
        <StatusBadge status={product.status} />
      </div>
      <p className="mt-3 text-sm text-ink-soft">
        {product.status === 'ACTIVE'
          ? t('admin.product.publish.live')
          : product.status === 'ARCHIVED'
            ? t('admin.product.publish.archived')
            : t('admin.product.publish.draft')}
      </p>

      {problems.length > 0 && (
        <div className="mt-4 rounded-xl border border-line bg-mist/60 p-4">
          <p className="text-sm font-medium">{t('admin.product.publish.missing')}</p>
          <ul className="mt-2 space-y-1.5 text-sm text-ink-soft">
            {problems.map((problem) => (
              <li key={problem} className="flex gap-2">
                <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-danger" />
                {t(`admin.product.publish.problems.${problem}`)}
              </li>
            ))}
          </ul>
        </div>
      )}
      {problems.length === 0 && product.status !== 'ACTIVE' && (
        <p className="mt-4 flex gap-2 text-sm text-ink-soft">
          <CircleCheck aria-hidden className="mt-0.5 size-4 shrink-0 text-success" />
          {t('admin.product.publish.ready')}
        </p>
      )}
      {error && (
        <Alert tone="error" className="mt-4">
          {errorMessage(error)}
        </Alert>
      )}

      <div className="mt-5 flex flex-col gap-3">
        {product.status !== 'ACTIVE' && (
          <Button
            disabled={problems.length > 0 || publish.isPending}
            onClick={() => publish.mutate(undefined)}
          >
            {t('admin.product.publish.publish')}
          </Button>
        )}
        {product.status === 'ACTIVE' && (
          <Link
            to={`/products/${product.slug}`}
            target="_blank"
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-full border border-ink/20 px-6 text-[0.9375rem] font-medium hover:bg-mist"
          >
            <ExternalLink aria-hidden className="size-4" />
            {t('admin.product.viewInShop')}
          </Link>
        )}
        {product.status !== 'ARCHIVED' && (
          <Button variant="secondary" onClick={() => setConfirmArchive(true)}>
            {t('admin.product.publish.archive')}
          </Button>
        )}
        <Button
          variant="ghost"
          disabled={duplicate.isPending}
          aria-describedby="duplicate-hint"
          onClick={() =>
            duplicate.mutate(undefined, { onSuccess: (copy) => void navigate(`/admin/products/${copy.id}`) })
          }
        >
          <Copy aria-hidden className="size-4" />
          {t('admin.product.publish.duplicate')}
        </Button>
        <p id="duplicate-hint" className="text-xs text-muted">
          {t('admin.product.publish.duplicateHint')}
        </p>
      </div>

      <div className="mt-5 border-t border-line pt-4">
        <p className="text-sm text-ink-soft">
          {t('admin.product.publish.stock', { count: product.stockUnits })}
        </p>
        <Link
          to={`/admin/inventory?q=${encodeURIComponent(product.slug)}`}
          className="mt-1 inline-flex min-h-11 items-center text-sm font-medium text-brand-700 underline-offset-4 hover:underline"
        >
          {t('admin.product.publish.manageStock')}
        </Link>
      </div>

      <ConfirmDialog
        open={confirmArchive}
        onOpenChange={setConfirmArchive}
        title={t('admin.product.publish.archiveTitle')}
        description={t('admin.product.publish.archiveText')}
        confirmLabel={t('admin.product.publish.archive')}
        pending={archive.isPending}
        onConfirm={() => archive.mutate(undefined, { onSettled: () => setConfirmArchive(false) })}
      />
    </AdminCard>
  );
}
