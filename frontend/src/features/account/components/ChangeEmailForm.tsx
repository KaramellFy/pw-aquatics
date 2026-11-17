import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from '@/lib/validation/zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { PasswordField } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { useChangeEmail } from '../api/account';
import { GoogleConfirmation } from './GoogleConfirmation';

interface Values {
  newEmail: string;
  currentPassword: string;
}

interface ChangeEmailFormProps {
  currentEmail: string;
  hasPassword: boolean;
  /** Set while a confirmation with Google stands in for the password (from the server). */
  confirmedUntil: string | null;
}

export function ChangeEmailForm({ currentEmail, hasPassword, confirmedUntil }: ChangeEmailFormProps) {
  const { t, i18n } = useTranslation();
  const askPassword = hasPassword && !confirmedUntil;
  const change = useChangeEmail();
  const errorMessage = useApiErrorMessage();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const schema = useMemo(
    () =>
      z.object({
        newEmail: z.email(t('auth.validation.emailInvalid')).max(254, t('auth.validation.tooLong')),
        currentPassword: askPassword ? z.string().min(1, t('auth.validation.required')) : z.string(),
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, i18n.language, askPassword],
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
    const request = {
      newEmail: values.newEmail,
      currentPassword: askPassword ? values.currentPassword : undefined,
    };
    change.mutate(request, {
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
      {askPassword ? (
        <PasswordField
          label={t('auth.fields.currentPassword')}
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
      ) : (
        <GoogleConfirmation confirmedUntil={confirmedUntil} />
      )}
      <Button type="submit" variant="secondary" disabled={change.isPending}>
        {t('account.profile.emailSubmit')}
      </Button>
    </form>
  );
}
