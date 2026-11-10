import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useProfile } from '../api/account';
import { ChangeEmailForm } from '../components/ChangeEmailForm';
import { ChangePasswordForm } from '../components/ChangePasswordForm';
import { DeleteAccountSection } from '../components/DeleteAccountSection';
import { ProfileDetailsForm } from '../components/ProfileDetailsForm';

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border-t border-line py-10 first:border-t-0 first:pt-0">
      <h2 className="text-title">{title}</h2>
      <div className="mt-6 max-w-xl">{children}</div>
    </section>
  );
}

export function ProfilePage() {
  const { t } = useTranslation();
  const profile = useProfile();
  const errorMessage = useApiErrorMessage();

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
        {profile.data && (
          <>
            <Panel title={t('account.profile.detailsTitle')}>
              <ProfileDetailsForm profile={profile.data} />
            </Panel>
            <Panel title={t('account.profile.emailTitle')}>
              {/* Re-keyed so the form resets once the address has actually changed. */}
              <ChangeEmailForm key={profile.data.email} currentEmail={profile.data.email} />
            </Panel>
            <Panel title={t('account.profile.passwordTitle')}>
              <ChangePasswordForm />
            </Panel>
            <Panel title={t('account.profile.deleteTitle')}>
              <DeleteAccountSection />
            </Panel>
          </>
        )}
      </div>
    </>
  );
}
