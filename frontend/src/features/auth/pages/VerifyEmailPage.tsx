import { CheckCircle2 } from 'lucide-react';
import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { ButtonLink } from '@/components/ui/Button';
import { PageMeta } from '@/lib/seo/PageMeta';
import { useSession, useVerifyEmail } from '../api/auth';
import { AuthCard } from '../components/AuthCard';

/** Landing page of the emailed confirmation link (registration and email change). */
export function VerifyEmailPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const session = useSession();
  const { mutate, isSuccess, isError } = useVerifyEmail();
  const submitted = useRef(false);

  // The token works only once, so send it exactly once (also under React StrictMode).
  useEffect(() => {
    if (token && !submitted.current) {
      submitted.current = true;
      mutate(token);
    }
  }, [token, mutate]);

  const signedIn = session.data?.authenticated === true;
  const failed = !token || isError;

  return (
    <>
      <PageMeta title={t('auth.verify.metaTitle')} noIndex />
      {isSuccess ? (
        <AuthCard title={t('auth.verify.successTitle')}>
          <div role="status">
            <CheckCircle2 aria-hidden className="size-10 text-success" strokeWidth={1.2} />
            <p className="mt-4 text-ink-soft">
              {signedIn ? t('auth.verify.successTextSignedIn') : t('auth.verify.successText')}
            </p>
          </div>
          <ButtonLink to={signedIn ? '/account' : '/login'} size="lg" className="mt-8 w-full">
            {signedIn ? t('auth.verify.toAccount') : t('auth.signIn')}
          </ButtonLink>
        </AuthCard>
      ) : failed ? (
        <AuthCard title={t('auth.verify.failedTitle')}>
          <p role="alert" className="text-ink-soft">
            {t('auth.verify.failedText')}
          </p>
          <ButtonLink to={signedIn ? '/account' : '/login'} size="lg" className="mt-8 w-full">
            {signedIn ? t('auth.verify.toAccount') : t('auth.signIn')}
          </ButtonLink>
        </AuthCard>
      ) : (
        <AuthCard title={t('auth.verify.working')}>
          <div role="status" aria-busy className="h-2 w-full animate-pulse rounded-full bg-brand-100" />
        </AuthCard>
      )}
    </>
  );
}
