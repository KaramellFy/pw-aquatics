import { zodResolver } from '@hookform/resolvers/zod';
import { MailCheck } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useForgotPassword } from '../api/auth';
import { AuthCard } from '../components/AuthCard';

export function ForgotPasswordPage() {
  const { t, i18n } = useTranslation();
  const forgot = useForgotPassword();
  const errorMessage = useApiErrorMessage();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const schema = useMemo(
    () => z.object({ email: z.email(t('auth.validation.emailInvalid')) }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, i18n.language],
  );
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<{ email: string }>({ resolver: zodResolver(schema), defaultValues: { email: '' } });

  const backLink = (
    <Link to="/login" className="font-medium text-brand-700 underline-offset-4 hover:underline">
      {t('auth.forgot.back')}
    </Link>
  );

  return (
    <>
      <PageMeta title={t('auth.forgot.metaTitle')} noIndex />
      {sentTo ? (
        <AuthCard title={t('auth.forgot.sentTitle')} footer={backLink}>
          <div role="status">
            <MailCheck aria-hidden className="size-10 text-brand-700" strokeWidth={1.2} />
            <p className="mt-4 leading-relaxed text-ink-soft">
              {t('auth.forgot.sentText', { email: sentTo })}
            </p>
          </div>
        </AuthCard>
      ) : (
        <AuthCard title={t('auth.forgot.title')} lead={t('auth.forgot.lead')} footer={backLink}>
          <form
            onSubmit={handleSubmit(({ email }) =>
              forgot.mutate(email, { onSuccess: () => setSentTo(email) }),
            )}
            noValidate
            className="space-y-5"
          >
            {forgot.isError && <Alert tone="error">{errorMessage(forgot.error)}</Alert>}
            <TextField
              label={t('auth.fields.email')}
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
            <Button type="submit" size="lg" className="w-full" disabled={forgot.isPending}>
              {t('auth.forgot.submit')}
            </Button>
          </form>
        </AuthCard>
      )}
    </>
  );
}
