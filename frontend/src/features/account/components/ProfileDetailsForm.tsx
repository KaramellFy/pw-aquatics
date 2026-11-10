import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { CheckboxField, TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { currentLanguage } from '@/lib/i18n';
import { useUpdateProfile, type Profile } from '../api/account';

function profileSchema(t: TFunction) {
  const name = z.string().trim().min(1, t('auth.validation.required')).max(100, t('auth.validation.tooLong'));
  return z.object({
    firstName: name,
    lastName: name,
    phone: z
      .string()
      .trim()
      .max(40, t('auth.validation.tooLong'))
      .regex(/^[0-9+()/ .-]*$/, t('auth.validation.phoneInvalid')),
    marketingOptIn: z.boolean(),
  });
}
type ProfileValues = z.infer<ReturnType<typeof profileSchema>>;

export function ProfileDetailsForm({ profile }: { profile: Profile }) {
  const { t, i18n } = useTranslation();
  const update = useUpdateProfile();
  const errorMessage = useApiErrorMessage();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => profileSchema(t), [t, i18n.language]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ProfileValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      firstName: profile.firstName,
      lastName: profile.lastName,
      phone: profile.phone ?? '',
      marketingOptIn: profile.marketingOptIn,
    },
  });

  const onSubmit = handleSubmit((values) =>
    update.mutate(
      // Emails from the shop follow the language the customer uses the site in.
      { ...values, phone: values.phone || undefined, locale: currentLanguage() },
      { onError: (error) => applyServerFieldErrors(error, setError, ['firstName', 'lastName', 'phone']) },
    ),
  );

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {update.isError && <Alert tone="error">{errorMessage(update.error)}</Alert>}
      {update.isSuccess && <Alert tone="success">{t('account.profile.saved')}</Alert>}
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField
          label={t('auth.fields.firstName')}
          autoComplete="given-name"
          error={errors.firstName?.message}
          {...register('firstName')}
        />
        <TextField
          label={t('auth.fields.lastName')}
          autoComplete="family-name"
          error={errors.lastName?.message}
          {...register('lastName')}
        />
      </div>
      <TextField
        label={t('account.profile.phone')}
        type="tel"
        autoComplete="tel"
        hint={t('account.profile.phoneHint')}
        error={errors.phone?.message}
        {...register('phone')}
      />
      <CheckboxField label={t('auth.register.marketing')} {...register('marketingOptIn')} />
      <Button type="submit" disabled={update.isPending}>
        {update.isPending ? t('account.profile.saving') : t('account.profile.save')}
      </Button>
    </form>
  );
}
