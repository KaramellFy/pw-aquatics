import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { Pencil, Plus } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { CheckboxField, TextField } from '@/components/ui/Field';
import { Price } from '@/components/ui/Price';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { moneyInputValue, parseMoneyInput } from '@/lib/format/money';
import {
  useAddVariant,
  useUpdateVariant,
  type AdminProduct,
  type AdminVariant,
  type VariantInput,
} from '../api/products';
import { AdminCard } from './AdminCard';
import { DataTable, Td, Th } from './DataTable';
import { Pill } from './StatusBadge';

function variantSchema(t: TFunction) {
  return z
    .object({
      name: z.string().trim().min(1, t('admin.common.required')).max(100, t('admin.common.tooLong')),
      packSize: z.string().regex(/^[1-9]\d{0,3}$/, t('admin.common.wholeNumberInvalid')),
      sku: z
        .string()
        .trim()
        .min(1, t('admin.common.required'))
        .max(64, t('admin.common.tooLong'))
        .regex(/^[A-Za-z0-9._-]+$/, t('admin.product.variants.skuHint')),
      price: z
        .string()
        .refine((value) => parseMoneyInput(value) !== null, t('admin.product.variants.priceInvalid')),
      compareAtPrice: z
        .string()
        .refine(
          (value) => value.trim() === '' || parseMoneyInput(value) !== null,
          t('admin.product.variants.priceInvalid'),
        ),
      active: z.boolean(),
    })
    .refine(
      (values) =>
        values.compareAtPrice.trim() === '' ||
        (parseMoneyInput(values.compareAtPrice) ?? 0) > (parseMoneyInput(values.price) ?? 0),
      { path: ['compareAtPrice'], message: t('apiErrors.COMPARE_AT_PRICE_INVALID') },
    );
}
type VariantValues = z.infer<ReturnType<typeof variantSchema>>;

/** Turns the typed values into the request; prices typed as "3,90" become cents. */
export function toVariantInput(values: VariantValues): VariantInput {
  return {
    name: values.name,
    packSize: Number(values.packSize),
    sku: values.sku.trim().toUpperCase(),
    priceCents: parseMoneyInput(values.price) ?? 0,
    compareAtPriceCents: values.compareAtPrice.trim()
      ? (parseMoneyInput(values.compareAtPrice) ?? undefined)
      : undefined,
    active: values.active,
  };
}

/** The product's pack sizes with prices; add and edit in a dialog. */
export function VariantsEditor({ product }: { product: AdminProduct }) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState<{ variant?: AdminVariant } | null>(null);

  return (
    <AdminCard
      title={t('admin.product.sections.variants')}
      id="product-variants"
      actions={
        <Button variant="secondary" size="sm" className="min-h-11" onClick={() => setEditing({})}>
          <Plus aria-hidden className="size-4" />
          {t('admin.product.variants.add')}
        </Button>
      }
    >
      {product.variants.length === 0 ? (
        <p className="text-ink-soft">{t('admin.product.variants.empty')}</p>
      ) : (
        <DataTable
          caption={t('admin.product.sections.variants')}
          head={
            <tr>
              <Th>{t('admin.product.variants.columns.name')}</Th>
              <Th>{t('admin.product.variants.columns.sku')}</Th>
              <Th className="text-right">{t('admin.product.variants.columns.price')}</Th>
              <Th>{t('admin.product.variants.columns.status')}</Th>
              <Th>
                <span className="sr-only">{t('admin.common.actions')}</span>
              </Th>
            </tr>
          }
        >
          {product.variants.map((variant) => (
            <tr key={variant.id}>
              <Td>
                <span className="font-medium">{variant.name}</span>
                <span className="block text-sm text-muted">
                  {t('admin.products.animals', { count: variant.packSize })}
                </span>
              </Td>
              <Td className="font-mono text-sm">{variant.sku}</Td>
              <Td className="text-right">
                <Price value={variant.price} compareAt={variant.compareAtPrice} />
              </Td>
              <Td>
                <Pill tone={variant.active ? 'ok' : 'neutral'}>
                  {variant.active
                    ? t('admin.product.variants.onSale')
                    : t('admin.product.variants.notOnSale')}
                </Pill>
              </Td>
              <Td className="text-right">
                <Button
                  variant="ghost"
                  aria-label={t('admin.product.variants.editLabel', { name: variant.name })}
                  onClick={() => setEditing({ variant })}
                >
                  <Pencil aria-hidden className="size-4" />
                  {t('admin.common.edit')}
                </Button>
              </Td>
            </tr>
          ))}
        </DataTable>
      )}

      <Dialog
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
        title={
          editing?.variant ? t('admin.product.variants.editTitle') : t('admin.product.variants.addTitle')
        }
      >
        {editing && (
          <VariantForm product={product} variant={editing.variant} onDone={() => setEditing(null)} />
        )}
      </Dialog>
    </AdminCard>
  );
}

function VariantForm({
  product,
  variant,
  onDone,
}: {
  product: AdminProduct;
  variant?: AdminVariant;
  onDone: () => void;
}) {
  const { t, i18n } = useTranslation();
  const add = useAddVariant(product.id);
  const update = useUpdateVariant(product.id);
  const save = variant ? update : add;
  const errorMessage = useApiErrorMessage();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => variantSchema(t), [t, i18n.language]);
  const skuBase = product.slug.toUpperCase().slice(0, 56);
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors, dirtyFields },
  } = useForm<VariantValues>({
    resolver: zodResolver(schema),
    defaultValues: variant
      ? {
          name: variant.name,
          packSize: String(variant.packSize),
          sku: variant.sku,
          price: moneyInputValue(variant.price.amount),
          compareAtPrice: variant.compareAtPrice ? moneyInputValue(variant.compareAtPrice.amount) : '',
          active: variant.active,
        }
      : { name: '', packSize: '1', sku: `${skuBase}-1`, price: '', compareAtPrice: '', active: true },
  });

  // A new pack size suggests its SKU from the web address and pack size until the SKU is typed.
  const packSize = useWatch({ control, name: 'packSize' });
  useEffect(() => {
    if (!variant && !dirtyFields.sku && /^\d+$/.test(packSize)) {
      setValue('sku', `${skuBase}-${packSize}`);
    }
  }, [packSize, variant, dirtyFields.sku, setValue, skuBase]);

  const onSubmit = handleSubmit((values) => {
    const input = toVariantInput(values);
    if (variant) update.mutate({ variantId: variant.id, input }, { onSuccess: onDone });
    else add.mutate(input, { onSuccess: onDone });
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      <TextField
        label={t('admin.product.variants.name')}
        placeholder={t('admin.product.variants.namePlaceholder')}
        error={errors.name?.message}
        {...register('name')}
      />
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField
          label={t('admin.product.variants.packSize')}
          inputMode="numeric"
          error={errors.packSize?.message}
          {...register('packSize')}
        />
        <TextField
          label={t('admin.product.variants.sku')}
          hint={t('admin.product.variants.skuHint')}
          error={errors.sku?.message}
          autoCapitalize="characters"
          spellCheck={false}
          {...register('sku')}
        />
        <TextField
          label={t('admin.product.variants.price')}
          inputMode="decimal"
          placeholder="3,90"
          error={errors.price?.message}
          {...register('price')}
        />
        <TextField
          label={t('admin.product.variants.compareAtPrice')}
          hint={t('admin.product.variants.compareAtPriceHint')}
          inputMode="decimal"
          error={errors.compareAtPrice?.message}
          {...register('compareAtPrice')}
        />
      </div>
      <CheckboxField label={t('admin.product.variants.onSale')} {...register('active')} />
      <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onDone}>
          {t('admin.common.cancel')}
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? t('admin.common.saving') : t('admin.common.save')}
        </Button>
      </div>
    </form>
  );
}
