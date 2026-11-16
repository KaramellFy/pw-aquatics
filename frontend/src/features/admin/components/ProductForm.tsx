import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo, type ReactNode } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { CheckboxField, SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import type { AdminProduct, ProductInput } from '../api/products';
import {
  categoryTree,
  useAdminCategories,
  useAdminColours,
  useAdminGrades,
  useAdminSpecies,
} from '../api/taxonomy';
import { AdminCard } from './AdminCard';
import { MarkdownField } from './MarkdownField';

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/** Mirrors the server's limits for quick feedback; the server checks everything again. */
function productSchema(t: TFunction) {
  const optional = (max: number) => z.string().trim().max(max, t('admin.common.tooLong'));
  return z.object({
    name: z.string().trim().min(1, t('admin.common.required')).max(150, t('admin.common.tooLong')),
    slug: optional(180).refine((value) => value === '' || SLUG.test(value), t('apiErrors.SLUG_INVALID')),
    categoryId: z.string().min(1, t('admin.common.required')),
    speciesId: z.string(),
    gradeId: z.string(),
    colourIds: z.array(z.string()),
    shortDescription: optional(300),
    description: optional(20000),
    careNotes: optional(10000),
    liveAnimal: z.boolean(),
    featured: z.boolean(),
    lowStockThreshold: z.string().regex(/^\d{1,5}$/, t('admin.common.wholeNumberInvalid')),
    availableFrom: z.string(),
    taxRateBps: z.string(),
    seoTitle: optional(160),
    seoDescription: optional(320),
    nameDe: optional(150),
    shortDescriptionDe: optional(300),
    descriptionDe: optional(20000),
    careNotesDe: optional(10000),
    seoTitleDe: optional(160),
    seoDescriptionDe: optional(320),
  });
}
export type ProductFormValues = z.infer<ReturnType<typeof productSchema>>;
const FIELDS = [
  'name',
  'slug',
  'categoryId',
  'speciesId',
  'gradeId',
  'shortDescription',
  'description',
  'careNotes',
  'lowStockThreshold',
  'availableFrom',
  'taxRateBps',
  'seoTitle',
  'seoDescription',
  'nameDe',
  'shortDescriptionDe',
  'descriptionDe',
  'careNotesDe',
  'seoTitleDe',
  'seoDescriptionDe',
] as const;

function toValues(product?: AdminProduct): ProductFormValues {
  return {
    name: product?.name ?? '',
    slug: product?.slug ?? '',
    categoryId: product?.categoryId ?? '',
    speciesId: product?.speciesId ?? '',
    gradeId: product?.gradeId ?? '',
    colourIds: product?.colourIds ?? [],
    shortDescription: product?.shortDescription ?? '',
    description: product?.description ?? '',
    careNotes: product?.careNotes ?? '',
    liveAnimal: product?.liveAnimal ?? true,
    featured: product?.featured ?? false,
    lowStockThreshold: String(product?.lowStockThreshold ?? 5),
    availableFrom: product?.availableFrom ?? '',
    taxRateBps: String(product?.taxRateBps ?? 1900),
    seoTitle: product?.seoTitle ?? '',
    seoDescription: product?.seoDescription ?? '',
    nameDe: product?.nameDe ?? '',
    shortDescriptionDe: product?.shortDescriptionDe ?? '',
    descriptionDe: product?.descriptionDe ?? '',
    careNotesDe: product?.careNotesDe ?? '',
    seoTitleDe: product?.seoTitleDe ?? '',
    seoDescriptionDe: product?.seoDescriptionDe ?? '',
  };
}

/** Empty optional fields are left out of the request. */
export function toProductInput(values: ProductFormValues, version?: number): ProductInput {
  return {
    name: values.name,
    slug: values.slug || undefined,
    categoryId: values.categoryId,
    speciesId: values.speciesId || undefined,
    gradeId: values.gradeId || undefined,
    colourIds: values.colourIds,
    shortDescription: values.shortDescription || undefined,
    description: values.description || undefined,
    careNotes: values.careNotes || undefined,
    liveAnimal: values.liveAnimal,
    featured: values.featured,
    lowStockThreshold: Number(values.lowStockThreshold),
    availableFrom: values.availableFrom || undefined,
    taxRateBps: Number(values.taxRateBps),
    seoTitle: values.seoTitle || undefined,
    seoDescription: values.seoDescription || undefined,
    nameDe: values.nameDe || undefined,
    shortDescriptionDe: values.shortDescriptionDe || undefined,
    descriptionDe: values.descriptionDe || undefined,
    careNotesDe: values.careNotesDe || undefined,
    seoTitleDe: values.seoTitleDe || undefined,
    seoDescriptionDe: values.seoDescriptionDe || undefined,
    version,
  };
}

interface ProductFormProps {
  /** The product to edit; omit to create one. */
  product?: AdminProduct;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  /** @param onServerError call it with an error from the save request to mark fields */
  onSubmit: (values: ProductFormValues, onServerError: (error: unknown) => void) => void;
  /** Shown above the form, e.g. after saving or on an edit conflict. */
  notice?: ReactNode;
}

/** The product's details, texts and settings (everything except pack sizes, photos and stock). */
export function ProductForm({ product, submitLabel, pending, error, onSubmit, notice }: ProductFormProps) {
  const { t, i18n } = useTranslation();
  const errorMessage = useApiErrorMessage();
  const categories = useAdminCategories();
  const species = useAdminSpecies();
  const grades = useAdminGrades();
  const colours = useAdminColours();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => productSchema(t), [t, i18n.language]);
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<ProductFormValues>({ resolver: zodResolver(schema), defaultValues: toValues(product) });

  // Field errors from the server (rare: the form mirrors its limits) appear next to the field.
  const submit = handleSubmit((values) =>
    onSubmit(values, (serverError) => {
      applyServerFieldErrors(serverError, setError, FIELDS);
    }),
  );
  const taxRate = useWatch({ control, name: 'taxRateBps' });
  const description = useWatch({ control, name: 'description' });
  const careNotes = useWatch({ control, name: 'careNotes' });
  const descriptionDe = useWatch({ control, name: 'descriptionDe' });
  const careNotesDe = useWatch({ control, name: 'careNotesDe' });
  const german = (label: string) => t('admin.common.inGerman', { label });
  const conflict = error instanceof ApiError && error.code === 'CONFLICT';
  const slugError =
    errors.slug?.message ??
    (error instanceof ApiError && (error.code === 'SLUG_TAKEN' || error.code === 'SLUG_INVALID')
      ? errorMessage(error)
      : undefined);

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      {notice}
      {Boolean(error) && !conflict && !slugError && <Alert tone="error">{errorMessage(error)}</Alert>}

      <AdminCard title={t('admin.product.sections.details')} id="product-details">
        <div className="grid gap-5 md:grid-cols-2">
          <TextField
            label={t('admin.product.fields.name')}
            error={errors.name?.message}
            className="md:col-span-2"
            {...register('name')}
          />
          <TextField
            label={t('admin.common.slug')}
            hint={t('admin.common.slugHint')}
            error={slugError}
            autoCapitalize="none"
            spellCheck={false}
            className="md:col-span-2"
            {...register('slug')}
          />
          <SelectField
            label={t('admin.product.fields.category')}
            error={errors.categoryId?.message}
            {...register('categoryId')}
          >
            <option value="">{t('admin.product.fields.chooseCategory')}</option>
            {categoryTree(categories.data ?? []).map(({ category, depth }) => (
              <option key={category.id} value={category.id}>
                {' '.repeat(depth) + category.name}
              </option>
            ))}
          </SelectField>
          <SelectField label={t('admin.product.fields.species')} {...register('speciesId')}>
            <option value="">{t('admin.product.fields.noSpecies')}</option>
            {(species.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>
                {item.scientificName}
                {item.commonName ? ` (${item.commonName})` : ''}
              </option>
            ))}
          </SelectField>
          <SelectField label={t('admin.product.fields.grade')} {...register('gradeId')}>
            <option value="">{t('admin.product.fields.noGrade')}</option>
            {(grades.data ?? []).map((grade) => (
              <option key={grade.id} value={grade.id}>
                {grade.name}
              </option>
            ))}
          </SelectField>
          <fieldset className="md:col-span-2">
            <legend className="text-sm font-medium text-ink-soft">{t('admin.product.fields.colours')}</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {(colours.data ?? []).map((colour) => (
                <label
                  key={colour.id}
                  className="inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-full border border-line-strong px-3 has-checked:border-brand-700 has-checked:bg-brand-50"
                >
                  <input
                    type="checkbox"
                    value={colour.id}
                    className="size-4 accent-brand-700"
                    {...register('colourIds')}
                  />
                  {colour.hexCode && (
                    <span
                      aria-hidden
                      className="size-3.5 rounded-full border border-ink/10"
                      style={{ backgroundColor: colour.hexCode }}
                    />
                  )}
                  <span className="text-sm">{colour.name}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <CheckboxField label={t('admin.product.fields.liveAnimal')} {...register('liveAnimal')} />
          <CheckboxField label={t('admin.product.fields.featured')} {...register('featured')} />
        </div>
      </AdminCard>

      <AdminCard title={t('admin.product.sections.texts')} id="product-texts">
        <div className="space-y-5">
          <TextAreaField
            label={t('admin.product.fields.shortDescription')}
            hint={t('admin.product.fields.shortDescriptionHint')}
            error={errors.shortDescription?.message}
            rows={2}
            maxLength={300}
            {...register('shortDescription')}
          />
          <MarkdownField
            label={t('admin.product.fields.description')}
            value={description}
            error={errors.description?.message}
            {...register('description')}
          />
          <MarkdownField
            label={t('admin.product.fields.careNotes')}
            value={careNotes}
            error={errors.careNotes?.message}
            rows={5}
            {...register('careNotes')}
          />
        </div>
      </AdminCard>

      <AdminCard title={t('admin.product.sections.settings')} id="product-settings">
        <div className="grid gap-5 md:grid-cols-3">
          <TextField
            label={t('admin.product.fields.lowStockThreshold')}
            hint={t('admin.product.fields.lowStockThresholdHint')}
            error={errors.lowStockThreshold?.message}
            inputMode="numeric"
            {...register('lowStockThreshold')}
          />
          <TextField
            type="date"
            label={t('admin.product.fields.availableFrom')}
            hint={t('admin.product.fields.availableFromHint')}
            {...register('availableFrom')}
          />
          <SelectField label={t('admin.product.fields.taxRate')} {...register('taxRateBps')}>
            <option value="1900">{t('admin.product.fields.taxRate1900')}</option>
            <option value="700">{t('admin.product.fields.taxRate700')}</option>
            {taxRate !== '1900' && taxRate !== '700' && (
              <option value={taxRate}>{`${Number(taxRate) / 100} %`}</option>
            )}
          </SelectField>
        </div>
      </AdminCard>

      <AdminCard title={t('admin.product.sections.seo')} id="product-seo">
        <div className="space-y-5">
          <TextField
            label={t('admin.product.fields.seoTitle')}
            hint={t('admin.product.fields.seoTitleHint')}
            error={errors.seoTitle?.message}
            maxLength={160}
            {...register('seoTitle')}
          />
          <TextAreaField
            label={t('admin.product.fields.seoDescription')}
            error={errors.seoDescription?.message}
            rows={2}
            maxLength={320}
            {...register('seoDescription')}
          />
        </div>
      </AdminCard>

      <AdminCard title={t('admin.product.sections.german')} id="product-german">
        <p className="mb-5 text-sm text-ink-soft">{t('admin.product.germanIntro')}</p>
        <div className="space-y-5">
          <TextField
            label={german(t('admin.product.fields.name'))}
            error={errors.nameDe?.message}
            maxLength={150}
            lang="de"
            {...register('nameDe')}
          />
          <TextAreaField
            label={german(t('admin.product.fields.shortDescription'))}
            error={errors.shortDescriptionDe?.message}
            rows={2}
            maxLength={300}
            lang="de"
            {...register('shortDescriptionDe')}
          />
          <MarkdownField
            label={german(t('admin.product.fields.description'))}
            value={descriptionDe}
            error={errors.descriptionDe?.message}
            lang="de"
            {...register('descriptionDe')}
          />
          <MarkdownField
            label={german(t('admin.product.fields.careNotes'))}
            value={careNotesDe}
            error={errors.careNotesDe?.message}
            rows={5}
            lang="de"
            {...register('careNotesDe')}
          />
          <TextField
            label={german(t('admin.product.fields.seoTitle'))}
            error={errors.seoTitleDe?.message}
            maxLength={160}
            lang="de"
            {...register('seoTitleDe')}
          />
          <TextAreaField
            label={german(t('admin.product.fields.seoDescription'))}
            error={errors.seoDescriptionDe?.message}
            rows={2}
            maxLength={320}
            lang="de"
            {...register('seoDescriptionDe')}
          />
        </div>
      </AdminCard>

      <div className="flex justify-end">
        <Button type="submit" size="lg" disabled={pending}>
          {pending ? t('admin.common.saving') : submitLabel}
        </Button>
      </div>
    </form>
  );
}
