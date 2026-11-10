import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { PasswordField } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { useChangePassword } from '../api/account';

interface Values {
  currentPassword: string;
  newPassword: string;
}

export function ChangePasswordForm() {
  const { t, i18n } = useTranslation();
  const change = useChangePassword();
  const errorMessage = useApiErrorMessage();
  const schema = useMemo(
    () =>
      z.object({
        currentPassword: z.string().min(1, t('auth.validation.required')),
        newPassword: z
          .string()
          .min(10, t('auth.validation.passwordTooShort'))
          .max(128, t('auth.validation.tooLong')),
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
    defaultValues: { currentPassword: '', newPassword: '' },
  });

  const onSubmit = handleSubmit((values) =>
    change.mutate(values, {
      onSuccess: () => reset(),
      onError: (error) => applyServerFieldErrors(error, setError, ['currentPassword', 'newPassword']),
    }),
  );

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {change.isError && <Alert tone="error">{errorMessage(change.error)}</Alert>}
      {change.isSuccess && <Alert tone="success">{t('account.profile.passwordChanged')}</Alert>}
      <PasswordField
        label={t('auth.fields.currentPassword')}
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />
      <PasswordField
        label={t('auth.fields.newPassword')}
        autoComplete="new-password"
        hint={t('auth.fields.passwordHint')}
        error={errors.newPassword?.message}
        {...register('newPassword')}
      />
      <Button type="submit" variant="secondary" disabled={change.isPending}>
        {t('account.profile.passwordSubmit')}
      </Button>
    </form>
  );
}
