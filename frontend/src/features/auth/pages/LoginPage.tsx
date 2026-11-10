import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { ApiError } from '@/lib/api/client';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useLogin, useResendVerification, useSession } from '../api/auth';
import { AuthCard } from '../components/AuthCard';
import { PasswordField } from '../components/PasswordField';
import { safeRedirectTarget } from '../redirect';

function loginSchema(t: TFunction) {
  return z.object({
    email: z.email(t('auth.validation.emailInvalid')),
    password: z.string().min(1, t('auth.validation.required')),
  });
}
type LoginValues = z.infer<ReturnType<typeof loginSchema>>;

export function LoginPage() {
  const { t, i18n } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const session = useSession();
  const login = useLogin();
  const resend = useResendVerification();
  const errorMessage = useApiErrorMessage();
  const target = safeRedirectTarget(searchParams.get('redirect'));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => loginSchema(t), [t, i18n.language]);
  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<LoginValues>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  if (session.data?.authenticated && !login.isPending) {
    return <Navigate to={target} replace />;
  }

  const onSubmit = handleSubmit((values) => {
    resend.reset();
    login.mutate(values, { onSuccess: () => navigate(target, { replace: true }) });
  });
  const notVerified = login.error instanceof ApiError && login.error.code === 'EMAIL_NOT_VERIFIED';

  return (
    <>
      <PageMeta title={t('auth.login.metaTitle')} noIndex />
      <AuthCard
        title={t('auth.login.title')}
        lead={t('auth.login.lead')}
        footer={
          <>
            {t('auth.login.noAccount')}{' '}
            <Link to="/register" className="font-medium text-brand-700 underline-offset-4 hover:underline">
              {t('auth.login.createAccount')}
            </Link>
          </>
        }
      >
        <form onSubmit={onSubmit} noValidate className="space-y-5">
          {login.isError && (
            <Alert tone="error">
              <p>{errorMessage(login.error)}</p>
              {notVerified && !resend.isSuccess && (
                <Button
                  variant="link"
                  className="mt-2 text-sm"
                  disabled={resend.isPending}
                  onClick={() => resend.mutate(getValues('email'))}
                >
                  {t('auth.login.resend')}
                </Button>
              )}
              {resend.isSuccess && <p className="mt-2">{t('auth.login.resent')}</p>}
            </Alert>
          )}
          <TextField
            label={t('auth.fields.email')}
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register('email')}
          />
          <PasswordField
            label={t('auth.fields.password')}
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <div className="flex justify-end">
            <Link to="/forgot-password" className="text-sm text-brand-700 underline-offset-4 hover:underline">
              {t('auth.login.forgot')}
            </Link>
          </div>
          <Button type="submit" size="lg" className="w-full" disabled={login.isPending}>
            {login.isPending ? t('auth.login.submitting') : t('auth.login.submit')}
          </Button>
        </form>
      </AuthCard>
    </>
  );
}
