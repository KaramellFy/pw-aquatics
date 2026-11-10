import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { PasswordField } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { useChangeEmail } from '../api/account';

interface Values {
  newEmail: string;
  currentPassword: string;
}

export function ChangeEmailForm({ currentEmail }: { currentEmail: string }) {
  const { t, i18n } = useTranslation();
  const change = useChangeEmail();
  const errorMessage = useApiErrorMessage();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const schema = useMemo(
    () =>
      z.object({
        newEmail: z.email(t('auth.validation.emailInvalid')).max(254, t('auth.validation.tooLong')),
        currentPassword: z.string().min(1, t('auth.validation.required')),
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, i18n.language],
  );
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { newEmail: '', currentPassword: '' },
  });

  const onSubmit = handleSubmit((values) => {
    setSentTo(null);
    change.mutate(values, {
      onSuccess: () => {
        setSentTo(values.newEmail);
        reset();
      },
      onError: (error) => applyServerFieldErrors(error, setError, ['newEmail', 'currentPassword']),
    });
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <p className="text-ink-soft">{t('account.profile.emailCurrent', { email: currentEmail })}</p>
      {change.isError && <Alert tone="error">{errorMessage(change.error)}</Alert>}
      {sentTo && <Alert tone="success">{t('account.profile.emailSent', { email: sentTo })}</Alert>}
      <TextField
        label={t('account.profile.newEmail')}
        type="email"
        autoComplete="email"
        error={errors.newEmail?.message}
        {...register('newEmail')}
      />
      <PasswordField
        label={t('auth.fields.currentPassword')}
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />
      <Button type="submit" variant="secondary" disabled={change.isPending}>
        {t('account.profile.emailSubmit')}
      </Button>
    </form>
  );
}
