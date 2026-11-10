import { zodResolver } from '@hookform/resolvers/zod';
import type { TFunction } from 'i18next';
import { MailCheck } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Trans, useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { CheckboxField, TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { applyServerFieldErrors } from '@/lib/forms/serverErrors';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useRegister, useResendVerification, useSession } from '../api/auth';
import { AuthCard } from '../components/AuthCard';
import { PasswordField } from '../components/PasswordField';

/** Mirrors the server's limits for instant feedback; the server validates again. */
function registerSchema(t: TFunction) {
  const name = z.string().trim().min(1, t('auth.validation.required')).max(100, t('auth.validation.tooLong'));
  return z.object({
    firstName: name,
    lastName: name,
    email: z.email(t('auth.validation.emailInvalid')).max(254, t('auth.validation.tooLong')),
    password: z
      .string()
      .min(10, t('auth.validation.passwordTooShort'))
      .max(128, t('auth.validation.tooLong')),
    marketingOptIn: z.boolean(),
  });
}
type RegisterValues = z.infer<ReturnType<typeof registerSchema>>;
const FIELDS = ['firstName', 'lastName', 'email', 'password'] as const;

export function RegisterPage() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const registration = useRegister();
  const resend = useResendVerification();
  const errorMessage = useApiErrorMessage();
  const [sentTo, setSentTo] = useState<string | null>(null);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const schema = useMemo(() => registerSchema(t), [t, i18n.language]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', password: '', marketingOptIn: false },
  });

  if (session.data?.authenticated) {
    return <Navigate to="/account" replace />;
  }

  const onSubmit = handleSubmit((values) =>
    registration.mutate(values, {
      onSuccess: () => setSentTo(values.email),
      onError: (error) => applyServerFieldErrors(error, setError, FIELDS),
    }),
  );

  if (sentTo) {
    return (
      <>
        <PageMeta title={t('auth.register.metaTitle')} noIndex />
        <AuthCard title={t('auth.register.sentTitle')}>
          <div role="status">
            <MailCheck aria-hidden className="size-10 text-brand-700" strokeWidth={1.2} />
            <p className="mt-4 leading-relaxed text-ink-soft">
              {t('auth.register.sentText', { email: sentTo })}
            </p>
          </div>
          <div className="mt-8 border-t border-line pt-6">
            {resend.isSuccess ? (
              <p role="status" className="text-sm text-ink-soft">
                {t('auth.register.resent')}
              </p>
            ) : (
              <Button variant="secondary" disabled={resend.isPending} onClick={() => resend.mutate(sentTo)}>
                {t('auth.register.resend')}
              </Button>
            )}
            {resend.isError && (
              <Alert tone="error" className="mt-4">
                {errorMessage(resend.error)}
              </Alert>
            )}
          </div>
        </AuthCard>
      </>
    );
  }

  return (
    <>
      <PageMeta title={t('auth.register.metaTitle')} noIndex />
      <AuthCard
        title={t('auth.register.title')}
        lead={t('auth.register.lead')}
        footer={
          <>
            {t('auth.register.haveAccount')}{' '}
            <Link to="/login" className="font-medium text-brand-700 underline-offset-4 hover:underline">
              {t('auth.signIn')}
            </Link>
          </>
        }
      >
        <form onSubmit={onSubmit} noValidate className="space-y-5">
          {registration.isError && <Alert tone="error">{errorMessage(registration.error)}</Alert>}
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
            label={t('auth.fields.email')}
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register('email')}
          />
          <PasswordField
            label={t('auth.fields.password')}
            autoComplete="new-password"
            hint={t('auth.fields.passwordHint')}
            error={errors.password?.message}
            {...register('password')}
          />
          <CheckboxField label={t('auth.register.marketing')} {...register('marketingOptIn')} />
          <Button type="submit" size="lg" className="w-full" disabled={registration.isPending}>
            {registration.isPending ? t('auth.register.submitting') : t('auth.register.submit')}
          </Button>
          <p className="text-sm leading-relaxed text-muted">
            <Trans
              i18nKey="auth.register.privacyNotice"
              components={{
                privacyLink: <Link to="/legal/privacy" className="underline underline-offset-4" />,
              }}
            />
          </p>
        </form>
      </AuthCard>
    </>
  );
}
