import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { CheckboxField, SelectField, TextField } from '@/components/ui/Field';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useAdminSpeciesDetail, useSaveSpecies, type AdminSpecies, type SpeciesInput } from '../api/taxonomy';
import { AdminCard } from '../components/AdminCard';
import { AdminPageHeader } from '../components/AdminPageHeader';
import { LoadState } from '../components/LoadState';
import { MarkdownField } from '../components/MarkdownField';

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const DECIMAL = /^-?\d{1,2}([.,]\d)?$/;
const WHOLE = /^\d{1,4}$/;
const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as const;
const DECIMAL_FIELDS = ['tempMinC', 'tempMaxC', 'phMin', 'phMax'] as const;
const WHOLE_FIELDS = [
  'ghMin',
  'ghMax',
  'khMin',
  'khMax',
  'tdsMin',
  'tdsMax',
  'adultSizeMm',
  'lifespanMonths',
] as const;

function speciesSchema(t: TFunction) {
  const decimal = z
    .string()
    .trim()
    .refine((v) => v === '' || DECIMAL.test(v), t('admin.common.numberInvalid'));
  const whole = z
    .string()
    .trim()
    .refine((v) => v === '' || WHOLE.test(v), t('admin.common.wholeNumberInvalid'));
  return z.object({
    scientificName: z.string().trim().min(1, t('admin.common.required')).max(150, t('admin.common.tooLong')),
    commonName: z.string().trim().max(150, t('admin.common.tooLong')),
    slug: z
      .string()
      .trim()
      .max(170, t('admin.common.tooLong'))
      .refine((v) => v === '' || SLUG.test(v), t('apiErrors.SLUG_INVALID')),
    origin: z.string().trim().max(150, t('admin.common.tooLong')),
    description: z.string().trim().max(10000, t('admin.common.tooLong')),
    tempMinC: decimal,
    tempMaxC: decimal,
    phMin: decimal,
    phMax: decimal,
    ghMin: whole,
    ghMax: whole,
    khMin: whole,
    khMax: whole,
    tdsMin: whole,
    tdsMax: whole,
    adultSizeMm: whole,
    lifespanMonths: whole,
    difficulty: z.string(),
    tradeRestricted: z.boolean(),
    restrictionNote: z.string().trim().max(1000, t('admin.common.tooLong')),
  });
}
type SpeciesValues = z.infer<ReturnType<typeof speciesSchema>>;

const text = (value: number | null | undefined) =>
  value === null || value === undefined ? '' : String(value);
const number = (value: string) => (value.trim() === '' ? undefined : Number(value.trim().replace(',', '.')));

function toValues(species?: AdminSpecies): SpeciesValues {
  return {
    scientificName: species?.scientificName ?? '',
    commonName: species?.commonName ?? '',
    slug: species?.slug ?? '',
    origin: species?.origin ?? '',
    description: species?.description ?? '',
    tempMinC: text(species?.tempMinC),
    tempMaxC: text(species?.tempMaxC),
    phMin: text(species?.phMin),
    phMax: text(species?.phMax),
    ghMin: text(species?.ghMin),
    ghMax: text(species?.ghMax),
    khMin: text(species?.khMin),
    khMax: text(species?.khMax),
    tdsMin: text(species?.tdsMin),
    tdsMax: text(species?.tdsMax),
    adultSizeMm: text(species?.adultSizeMm),
    lifespanMonths: text(species?.lifespanMonths),
    difficulty: species?.difficulty ?? '',
    tradeRestricted: species?.tradeRestricted ?? false,
    restrictionNote: species?.restrictionNote ?? '',
  };
}

function toInput(values: SpeciesValues): SpeciesInput {
  const input: SpeciesInput = {
    scientificName: values.scientificName,
    commonName: values.commonName || undefined,
    slug: values.slug || undefined,
    origin: values.origin || undefined,
    description: values.description || undefined,
    difficulty: (values.difficulty || undefined) as SpeciesInput['difficulty'],
    tradeRestricted: values.tradeRestricted,
    restrictionNote: values.tradeRestricted ? values.restrictionNote || undefined : undefined,
  };
  for (const field of [...DECIMAL_FIELDS, ...WHOLE_FIELDS]) {
    input[field] = number(values[field]);
  }
  return input;
}

/** Creates (`/admin/species/new`) or edits a species with its care data. */
export function AdminSpeciesEditPage() {
  const { id = 'new' } = useParams();
  const { t } = useTranslation();
  const creating = id === 'new';
  const species = useAdminSpeciesDetail(creating ? '' : id);
  const back = { to: '/admin/species', label: t('admin.species.back') };

  if (!creating && !species.data) {
    return (
      <>
        <AdminPageHeader title={t('admin.species.title')} back={back} />
        <LoadState
          isPending={species.isPending}
          isError={species.isError}
          hasData={false}
          onRetry={() => void species.refetch()}
        />
      </>
    );
  }
  return (
    <>
      <AdminPageHeader
        title={creating ? t('admin.species.newTitle') : species.data!.scientificName}
        back={back}
      />
      <SpeciesForm key={species.data?.id ?? 'new'} species={creating ? undefined : species.data} />
    </>
  );
}

function SpeciesForm({ species }: { species?: AdminSpecies }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const save = useSaveSpecies();
  const errorMessage = useApiErrorMessage();
  const [saved, setSaved] = useState(false);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => speciesSchema(t), [t, i18n.language]);
  const { register, handleSubmit, control, setError, formState } = useForm<SpeciesValues>({
    resolver: zodResolver(schema),
    defaultValues: toValues(species),
  });
  const errors = formState.errors;
  const restricted = useWatch({ control, name: 'tradeRestricted' });
  const description = useWatch({ control, name: 'description' });

  const onSubmit = handleSubmit((values) => {
    setSaved(false);
    save.mutate(
      { id: species?.id, input: toInput(values) },
      {
        onSuccess: (result) => {
          if (species) setSaved(true);
          else void navigate(`/admin/species/${result.id}`, { replace: true });
        },
        onError: (error) => {
          if (error instanceof ApiError && (error.code === 'SLUG_TAKEN' || error.code === 'SLUG_INVALID')) {
            setError('slug', { message: errorMessage(error) });
          }
        },
      },
    );
  });
  const field = (
    name: (typeof DECIMAL_FIELDS)[number] | (typeof WHOLE_FIELDS)[number],
    label: string,
    decimal = false,
  ) => (
    <TextField
      label={label}
      inputMode={decimal ? 'decimal' : 'numeric'}
      error={errors[name]?.message}
      {...register(name)}
    />
  );
  const slugError =
    save.error instanceof ApiError && ['SLUG_TAKEN', 'SLUG_INVALID'].includes(save.error.code);

  return (
    <form onSubmit={onSubmit} noValidate className="max-w-4xl space-y-6">
      {saved && !save.isError && <Alert tone="success">{t('admin.species.saved')}</Alert>}
      {save.isError && !slugError && <Alert tone="error">{errorMessage(save.error)}</Alert>}

      <AdminCard title={t('admin.species.sections.names')} id="species-names">
        <div className="grid gap-5 md:grid-cols-2">
          <TextField
            label={t('admin.species.fields.scientificName')}
            error={errors.scientificName?.message}
            className="md:col-span-2"
            {...register('scientificName')}
          />
          <TextField
            label={t('admin.species.fields.commonName')}
            error={errors.commonName?.message}
            {...register('commonName')}
          />
          <TextField
            label={t('admin.species.fields.origin')}
            error={errors.origin?.message}
            {...register('origin')}
          />
          <TextField
            label={t('admin.common.slug')}
            hint={t('admin.common.slugHint')}
            error={errors.slug?.message}
            autoCapitalize="none"
            spellCheck={false}
            className="md:col-span-2"
            {...register('slug')}
          />
          <MarkdownField
            label={t('admin.common.description')}
            value={description}
            error={errors.description?.message}
            className="md:col-span-2"
            {...register('description')}
          />
        </div>
      </AdminCard>

      <AdminCard title={t('admin.species.sections.care')} id="species-care">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {field('tempMinC', t('admin.species.fields.tempMin'), true)}
          {field('tempMaxC', t('admin.species.fields.tempMax'), true)}
          {field('phMin', t('admin.species.fields.phMin'), true)}
          {field('phMax', t('admin.species.fields.phMax'), true)}
          {field('ghMin', t('admin.species.fields.ghMin'))}
          {field('ghMax', t('admin.species.fields.ghMax'))}
          {field('khMin', t('admin.species.fields.khMin'))}
          {field('khMax', t('admin.species.fields.khMax'))}
          {field('tdsMin', t('admin.species.fields.tdsMin'))}
          {field('tdsMax', t('admin.species.fields.tdsMax'))}
          {field('adultSizeMm', t('admin.species.fields.adultSize'))}
          {field('lifespanMonths', t('admin.species.fields.lifespan'))}
          <SelectField
            label={t('admin.species.fields.difficulty')}
            className="sm:col-span-2"
            {...register('difficulty')}
          >
            <option value="">{t('admin.species.fields.noDifficulty')}</option>
            {DIFFICULTIES.map((value) => (
              <option key={value} value={value}>
                {t(`admin.species.fields.difficulties.${value}`)}
              </option>
            ))}
          </SelectField>
        </div>
      </AdminCard>

      <AdminCard title={t('admin.species.sections.trade')} id="species-trade">
        <CheckboxField label={t('admin.species.fields.tradeRestricted')} {...register('tradeRestricted')} />
        <p className="mt-2 pl-8 text-sm text-muted">{t('admin.species.fields.tradeRestrictedHint')}</p>
        {restricted && (
          <TextField
            label={t('admin.species.fields.restrictionNote')}
            error={errors.restrictionNote?.message}
            className="mt-5"
            {...register('restrictionNote')}
          />
        )}
      </AdminCard>

      <div className="flex justify-end">
        <Button type="submit" size="lg" disabled={save.isPending}>
          {save.isPending ? t('admin.common.saving') : t('admin.common.save')}
        </Button>
      </div>
    </form>
  );
}
