import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { googleProblem, useSession, useSignInOptions } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatLocalTime } from '@/lib/format/date';
import { useProfile } from '../api/account';
import { ChangeEmailForm } from '../components/ChangeEmailForm';
import { ChangePasswordForm } from '../components/ChangePasswordForm';
import { DeleteAccountSection } from '../components/DeleteAccountSection';
import { ProfileDetailsForm } from '../components/ProfileDetailsForm';
import { SignInSection } from '../components/SignInSection';
import { TwoFactorSection } from '../components/TwoFactorSection';

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border-t border-line py-10 first:border-t-0 first:pt-0">
      <h2 className="text-title">{title}</h2>
      <div className="mt-6 max-w-xl">{children}</div>
    </section>
  );
}

export function ProfilePage() {
  const { t, i18n } = useTranslation();
  const profile = useProfile();
  const options = useSignInOptions();
  const session = useSession();
  const errorMessage = useApiErrorMessage();
  // Back from confirming with Google: the server added ?google=confirmed or a problem.
  const [searchParams] = useSearchParams();
  const google = searchParams.get('google');
  const googleError = googleProblem(google);
  const confirmedUntil = profile.data?.confirmedUntil ?? null;

  return (
    <>
      <h1 className="text-headline">{t('account.profile.title')}</h1>
      <div className="mt-10">
        {profile.isError && <Alert tone="error">{errorMessage(profile.error)}</Alert>}
        {profile.isPending && (
          <p role="status" className="text-muted">
            {t('common.loading')}
          </p>
        )}
        {googleError && (
          <Alert tone="error" className="mb-8">
            {t(googleError)}
          </Alert>
        )}
        {google === 'confirmed' && confirmedUntil && (
          <Alert tone="success" className="mb-8">
            {t('account.profile.confirmedNotice', { time: formatLocalTime(confirmedUntil, i18n.language) })}
          </Alert>
        )}
        {profile.data && (
          <>
            <Panel title={t('account.profile.detailsTitle')}>
              <ProfileDetailsForm profile={profile.data} />
            </Panel>
            <Panel title={t('account.profile.emailTitle')}>
              {/* Re-keyed so the form resets once the address has actually changed. */}
              <ChangeEmailForm
                key={profile.data.email}
                currentEmail={profile.data.email}
                hasPassword={profile.data.hasPassword}
                confirmedUntil={confirmedUntil}
              />
            </Panel>
            {(options.data?.google || profile.data.googleEmail || !profile.data.hasPassword) && (
              <Panel title={t('account.profile.signInTitle')}>
                <SignInSection profile={profile.data} />
              </Panel>
            )}
            {session.data?.user?.role === 'ADMIN' && (
              <Panel title={t('account.twoFactor.title')}>
                <TwoFactorSection />
              </Panel>
            )}
            {profile.data.hasPassword && (
              <Panel title={t('account.profile.passwordTitle')}>
                <ChangePasswordForm />
              </Panel>
            )}
            <Panel title={t('account.profile.deleteTitle')}>
              <DeleteAccountSection hasPassword={profile.data.hasPassword} confirmedUntil={confirmedUntil} />
            </Panel>
          </>
        )}
      </div>
    </>
  );
}
