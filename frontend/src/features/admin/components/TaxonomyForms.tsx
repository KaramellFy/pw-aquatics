import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo, type ReactNode } from 'react';
import { useForm, useWatch, type FieldValues, type Path, type UseFormSetError } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { CheckboxField, SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import {
  categoryTree,
  useAdminCategories,
  useSaveCategory,
  useSaveColour,
  useSaveGrade,
  type AdminCategory,
  type AdminColour,
  type AdminGrade,
} from '../api/taxonomy';

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const wholeNumber = (t: TFunction, max: number) =>
  z
    .string()
    .regex(/^\d{1,4}$/, t('admin.common.wholeNumberInvalid'))
    .refine((value) => Number(value) <= max, t('admin.common.wholeNumberInvalid'));
const name = (t: TFunction, max: number) =>
  z.string().trim().min(1, t('admin.common.required')).max(max, t('admin.common.tooLong'));
const slug = (t: TFunction, max: number) =>
  z
    .string()
    .trim()
    .max(max, t('admin.common.tooLong'))
    .refine((value) => value === '' || SLUG.test(value), t('apiErrors.SLUG_INVALID'));

/** Shows a taken or invalid web address at the slug field, everything else above the form. */
function useServerError<T extends FieldValues>(setError: UseFormSetError<T>, fields: readonly Path<T>[]) {
  const errorMessage = useApiErrorMessage();
  return (error: unknown) => {
    if (error instanceof ApiError && (error.code === 'SLUG_TAKEN' || error.code === 'SLUG_INVALID')) {
      setError('slug' as Path<T>, { message: errorMessage(error) });
      return;
    }
    applyServerFieldErrors(error, setError, fields);
  };
}

function FormButtons({ pending, onCancel }: { pending: boolean; onCancel: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
      <Button variant="secondary" onClick={onCancel}>
        {t('admin.common.cancel')}
      </Button>
      <Button type="submit" disabled={pending}>
        {pending ? t('admin.common.saving') : t('admin.common.save')}
      </Button>
    </div>
  );
}

function ErrorNotice({ error }: { error: unknown }): ReactNode {
  const errorMessage = useApiErrorMessage();
  if (!error) return null;
  if (error instanceof ApiError && (error.code === 'SLUG_TAKEN' || error.code === 'SLUG_INVALID'))
    return null;
  return <Alert tone="error">{errorMessage(error)}</Alert>;
}

// ---------------------------------------------------------------------- categories

function categorySchema(t: TFunction) {
  return z.object({
    name: name(t, 100),
    slug: slug(t, 120),
    parentId: z.string(),
    description: z.string().trim().max(5000, t('admin.common.tooLong')),
    sortOrder: wholeNumber(t, 9999),
    active: z.boolean(),
  });
}
type CategoryValues = z.infer<ReturnType<typeof categorySchema>>;

export function CategoryForm({ category, onDone }: { category?: AdminCategory; onDone: () => void }) {
  const { t, i18n } = useTranslation();
  const save = useSaveCategory();
  const categories = useAdminCategories();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => categorySchema(t), [t, i18n.language]);
  const { register, handleSubmit, setError, formState } = useForm<CategoryValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: category?.name ?? '',
      slug: category?.slug ?? '',
      parentId: category?.parentId ?? '',
      description: category?.description ?? '',
      sortOrder: String(category?.sortOrder ?? 0),
      active: category?.active ?? true,
    },
  });
  const onServerError = useServerError(setError, ['name', 'slug', 'description', 'sortOrder']);
  const errors = formState.errors;
  // A category cannot become a child of itself or of one of its descendants.
  const tree = categoryTree(categories.data ?? []);
  const blocked = new Set<string>();
  if (category) {
    blocked.add(category.id);
    for (const { category: candidate } of tree) {
      if (candidate.parentId && blocked.has(candidate.parentId)) blocked.add(candidate.id);
    }
  }

  const onSubmit = handleSubmit((values) =>
    save.mutate(
      {
        id: category?.id,
        input: {
          name: values.name,
          slug: values.slug || undefined,
          parentId: values.parentId || undefined,
          description: values.description || undefined,
          sortOrder: Number(values.sortOrder),
          active: values.active,
        },
      },
      { onSuccess: onDone, onError: onServerError },
    ),
  );

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <ErrorNotice error={save.error} />
      <TextField label={t('admin.categories.name')} error={errors.name?.message} {...register('name')} />
      <TextField
        label={t('admin.common.slug')}
        hint={t('admin.common.slugHint')}
        error={errors.slug?.message}
        autoCapitalize="none"
        spellCheck={false}
        {...register('slug')}
      />
      <SelectField label={t('admin.categories.parent')} {...register('parentId')}>
        <option value="">{t('admin.categories.noParent')}</option>
        {tree
          .filter(({ category: candidate }) => !blocked.has(candidate.id))
          .map(({ category: candidate, depth }) => (
            <option key={candidate.id} value={candidate.id}>
              {' '.repeat(depth) + candidate.name}
            </option>
          ))}
      </SelectField>
      <TextAreaField
        label={t('admin.common.description')}
        rows={3}
        error={errors.description?.message}
        {...register('description')}
      />
      <TextField
        label={t('admin.common.sortOrder')}
        hint={t('admin.common.sortOrderHint')}
        inputMode="numeric"
        error={errors.sortOrder?.message}
        {...register('sortOrder')}
      />
      <CheckboxField label={t('admin.categories.visible')} {...register('active')} />
      <FormButtons pending={save.isPending} onCancel={onDone} />
    </form>
  );
}

// ---------------------------------------------------------------------- colours

function colourSchema(t: TFunction) {
  return z.object({
    name: name(t, 60),
    slug: slug(t, 80),
    hexCode: z
      .string()
      .trim()
      .refine((value) => value === '' || /^#[0-9A-Fa-f]{6}$/.test(value), t('admin.colours.hexInvalid')),
    sortOrder: wholeNumber(t, 9999),
  });
}
type ColourValues = z.infer<ReturnType<typeof colourSchema>>;

export function ColourForm({ colour, onDone }: { colour?: AdminColour; onDone: () => void }) {
  const { t, i18n } = useTranslation();
  const save = useSaveColour();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => colourSchema(t), [t, i18n.language]);
  const { register, handleSubmit, setError, setValue, control, formState } = useForm<ColourValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: colour?.name ?? '',
      slug: colour?.slug ?? '',
      hexCode: colour?.hexCode ?? '',
      sortOrder: String(colour?.sortOrder ?? 0),
    },
  });
  const onServerError = useServerError(setError, ['name', 'slug', 'hexCode', 'sortOrder']);
  const errors = formState.errors;
  const hex = useWatch({ control, name: 'hexCode' });

  const onSubmit = handleSubmit((values) =>
    save.mutate(
      {
        id: colour?.id,
        input: {
          name: values.name,
          slug: values.slug || undefined,
          hexCode: values.hexCode || undefined,
          sortOrder: Number(values.sortOrder),
        },
      },
      { onSuccess: onDone, onError: onServerError },
    ),
  );

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <ErrorNotice error={save.error} />
      <TextField label={t('admin.colours.name')} error={errors.name?.message} {...register('name')} />
      <TextField
        label={t('admin.common.slug')}
        hint={t('admin.common.slugHint')}
        error={errors.slug?.message}
        autoCapitalize="none"
        spellCheck={false}
        {...register('slug')}
      />
      <div className="flex items-end gap-3">
        <TextField
          label={t('admin.colours.hexCode')}
          hint={t('admin.colours.hexHint')}
          error={errors.hexCode?.message}
          placeholder="#1565C0"
          className="flex-1"
          {...register('hexCode')}
        />
        <input
          type="color"
          aria-label={t('admin.colours.hexCode')}
          value={/^#[0-9A-Fa-f]{6}$/.test(hex) ? hex : '#88C0DB'}
          onChange={(event) =>
            setValue('hexCode', event.target.value.toUpperCase(), { shouldValidate: true })
          }
          className="mb-7 h-12 w-14 cursor-pointer rounded-xl border border-line-strong bg-surface p-1"
        />
      </div>
      <TextField
        label={t('admin.common.sortOrder')}
        hint={t('admin.common.sortOrderHint')}
        inputMode="numeric"
        error={errors.sortOrder?.message}
        {...register('sortOrder')}
      />
      <FormButtons pending={save.isPending} onCancel={onDone} />
    </form>
  );
}

// ---------------------------------------------------------------------- grades

function gradeSchema(t: TFunction) {
  return z.object({
    name: name(t, 60),
    slug: slug(t, 80),
    scale: z.string().trim().max(50, t('admin.common.tooLong')),
    description: z.string().trim().max(1000, t('admin.common.tooLong')),
    rank: wholeNumber(t, 1000),
    sortOrder: wholeNumber(t, 9999),
  });
}
type GradeValues = z.infer<ReturnType<typeof gradeSchema>>;

export function GradeForm({ grade, onDone }: { grade?: AdminGrade; onDone: () => void }) {
  const { t, i18n } = useTranslation();
  const save = useSaveGrade();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => gradeSchema(t), [t, i18n.language]);
  const { register, handleSubmit, setError, formState } = useForm<GradeValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: grade?.name ?? '',
      slug: grade?.slug ?? '',
      scale: grade?.scale ?? '',
      description: grade?.description ?? '',
      rank: String(grade?.rank ?? 0),
      sortOrder: String(grade?.sortOrder ?? 0),
    },
  });
  const onServerError = useServerError(setError, [
    'name',
    'slug',
    'scale',
    'description',
    'rank',
    'sortOrder',
  ]);
  const errors = formState.errors;

  const onSubmit = handleSubmit((values) =>
    save.mutate(
      {
        id: grade?.id,
        input: {
          name: values.name,
          slug: values.slug || undefined,
          scale: values.scale || undefined,
          description: values.description || undefined,
          rank: Number(values.rank),
          sortOrder: Number(values.sortOrder),
        },
      },
      { onSuccess: onDone, onError: onServerError },
    ),
  );

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <ErrorNotice error={save.error} />
      <TextField label={t('admin.grades.name')} error={errors.name?.message} {...register('name')} />
      <TextField
        label={t('admin.common.slug')}
        hint={t('admin.common.slugHint')}
        error={errors.slug?.message}
        autoCapitalize="none"
        spellCheck={false}
        {...register('slug')}
      />
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField
          label={t('admin.grades.rank')}
          hint={t('admin.grades.rankHint')}
          inputMode="numeric"
          error={errors.rank?.message}
          {...register('rank')}
        />
        <TextField
          label={t('admin.common.sortOrder')}
          hint={t('admin.common.sortOrderHint')}
          inputMode="numeric"
          error={errors.sortOrder?.message}
          {...register('sortOrder')}
        />
      </div>
      <TextField
        label={t('admin.grades.scale')}
        hint={t('admin.grades.scaleHint')}
        error={errors.scale?.message}
        {...register('scale')}
      />
      <TextAreaField
        label={t('admin.common.description')}
        rows={3}
        error={errors.description?.message}
        {...register('description')}
      />
      <FormButtons pending={save.isPending} onCancel={onDone} />
    </form>
  );
}
