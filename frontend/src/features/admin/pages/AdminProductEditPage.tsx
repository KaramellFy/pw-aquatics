import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { ApiError } from '@/lib/api/client';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useAdminProduct, useCreateProduct, useUpdateProduct, type AdminProduct } from '../api/products';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { ImageManager } from '../components/ImageManager';
import { LoadState } from '../components/LoadState';
import { ProductForm, toProductInput } from '../components/ProductForm';
import { PublishPanel } from '../components/PublishPanel';
import { VariantsEditor } from '../components/VariantsEditor';

/** Creates a product; afterwards the editor opens for pack sizes, photos and publishing. */
export function AdminProductCreatePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateProduct();

  return (
    <>
      <PageMeta title={`${t('admin.product.newTitle')} – ${t('admin.metaTitle')}`} noIndex />
      <AdminPageHeader
        title={t('admin.product.newTitle')}
        lead={t('admin.product.createHint')}
        back={{ to: '/admin/products', label: t('admin.product.back') }}
      />
      <div className="max-w-4xl">
        <ProductForm
          submitLabel={t('admin.product.create')}
          pending={create.isPending}
          error={create.error}
          onSubmit={(values, onServerError) =>
            create.mutate(toProductInput(values), {
              onSuccess: (product) => void navigate(`/admin/products/${product.id}`, { replace: true }),
              onError: onServerError,
            })
          }
        />
      </div>
    </>
  );
}

export function AdminProductEditPage() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const product = useAdminProduct(id);

  if (!product.data) {
    const notFound = product.error instanceof ApiError && product.error.status === 404;
    return (
      <>
        <AdminPageHeader
          title={t('admin.product.back')}
          back={{ to: '/admin/products', label: t('admin.product.back') }}
        />
        {notFound ? (
          <Alert tone="error">{t('notFound.title')}</Alert>
        ) : (
          <LoadState
            isPending={product.isPending}
            isError={product.isError}
            hasData={false}
            onRetry={() => void product.refetch()}
          />
        )}
      </>
    );
  }
  // Re-mounted per product (e.g. after "duplicate"), so the form starts from that product.
  return (
    <ProductEditor key={product.data.id} product={product.data} onReload={() => void product.refetch()} />
  );
}

function ProductEditor({ product, onReload }: { product: AdminProduct; onReload: () => void }) {
  const { t } = useTranslation();
  const update = useUpdateProduct(product.id);
  const [saved, setSaved] = useState(false);
  const [formKey, setFormKey] = useState(0);
  const conflict = update.error instanceof ApiError && update.error.code === 'CONFLICT';

  const reload = () => {
    update.reset();
    onReload();
    setFormKey((key) => key + 1);
  };

  return (
    <>
      <PageMeta title={`${product.name} – ${t('admin.metaTitle')}`} noIndex />
      <AdminPageHeader
        title={product.name}
        back={{ to: '/admin/products', label: t('admin.product.back') }}
      />
      <div className="grid gap-6 xl:grid-cols-[1fr_20rem]">
        <div className="min-w-0 space-y-6">
          <ProductForm
            key={formKey}
            product={product}
            submitLabel={t('admin.common.save')}
            pending={update.isPending}
            error={update.error}
            notice={
              <>
                {saved && !update.isError && <Alert tone="success">{t('admin.product.saved')}</Alert>}
                {conflict && (
                  <Alert tone="error">
                    <p>{t('admin.product.conflict')}</p>
                    <Button variant="secondary" size="sm" className="mt-4" onClick={reload}>
                      {t('admin.common.reload')}
                    </Button>
                  </Alert>
                )}
              </>
            }
            onSubmit={(values, onServerError) => {
              setSaved(false);
              // The version of the product as last loaded or saved; the server refuses stale edits.
              update.mutate(toProductInput(values, product.version), {
                onSuccess: () => setSaved(true),
                onError: onServerError,
              });
            }}
          />
          <VariantsEditor product={product} />
          <ImageManager product={product} />
        </div>
        <div className="xl:sticky xl:top-24 xl:self-start">
          <PublishPanel product={product} />
        </div>
      </div>
    </>
  );
}
