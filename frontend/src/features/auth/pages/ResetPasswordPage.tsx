import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2 } from 'lucide-react';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useResetPassword } from '../api/auth';
import { AuthCard } from '../components/AuthCard';
import { PasswordField } from '../components/PasswordField';

/** Landing page of the emailed password-reset link. */
export function ResetPasswordPage() {
  const { t, i18n } = useTranslation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const reset = useResetPassword();
  const errorMessage = useApiErrorMessage();
  const schema = useMemo(
    () =>
      z.object({
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
    formState: { errors },
  } = useForm<{ newPassword: string }>({ resolver: zodResolver(schema), defaultValues: { newPassword: '' } });

  const linkInvalid = !token || (reset.error instanceof ApiError && reset.error.code === 'TOKEN_INVALID');

  let content;
  if (reset.isSuccess) {
    content = (
      <AuthCard title={t('auth.reset.successTitle')}>
        <div role="status">
          <CheckCircle2 aria-hidden className="size-10 text-success" strokeWidth={1.2} />
          <p className="mt-4 text-ink-soft">{t('auth.reset.successText')}</p>
        </div>
        <ButtonLink to="/login" size="lg" className="mt-8 w-full">
          {t('auth.signIn')}
        </ButtonLink>
      </AuthCard>
    );
  } else if (linkInvalid) {
    content = (
      <AuthCard title={t('auth.reset.invalidTitle')}>
        <p role="alert" className="text-ink-soft">
          {t('auth.reset.invalidText')}
        </p>
        <ButtonLink to="/forgot-password" size="lg" className="mt-8 w-full">
          {t('auth.reset.requestNew')}
        </ButtonLink>
      </AuthCard>
    );
  } else {
    content = (
      <AuthCard title={t('auth.reset.title')}>
        <form
          onSubmit={handleSubmit(({ newPassword }) =>
            reset.mutate(
              { token, newPassword },
              { onError: (error) => applyServerFieldErrors(error, setError, ['newPassword']) },
            ),
          )}
          noValidate
          className="space-y-5"
        >
          {reset.isError && <Alert tone="error">{errorMessage(reset.error)}</Alert>}
          <PasswordField
            label={t('auth.fields.newPassword')}
            autoComplete="new-password"
            hint={t('auth.fields.passwordHint')}
            error={errors.newPassword?.message}
            {...register('newPassword')}
          />
          <Button type="submit" size="lg" className="w-full" disabled={reset.isPending}>
            {t('auth.reset.submit')}
          </Button>
        </form>
      </AuthCard>
    );
  }

  return (
    <>
      <PageMeta title={t('auth.reset.metaTitle')} noIndex />
      {content}
    </>
  );
}
