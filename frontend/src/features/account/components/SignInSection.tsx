import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { useForgotPassword } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useDisconnectGoogle, type Profile } from '../api/account';

/**
 * How the customer signs in: whether Google is connected (and disconnecting it, possible while
 * there is a password), and, without a password, a link to set one.
 */
export function SignInSection({ profile }: { profile: Profile }) {
  const { t } = useTranslation();
  const disconnect = useDisconnectGoogle();
  const setPassword = useForgotPassword();
  const errorMessage = useApiErrorMessage();

  return (
    <div className="space-y-5">
      {disconnect.isSuccess && <Alert tone="success">{t('account.profile.googleDisconnected')}</Alert>}
      {disconnect.isError && <Alert tone="error">{errorMessage(disconnect.error)}</Alert>}
      {!profile.googleEmail && !disconnect.isSuccess && (
        <p className="text-ink-soft">{t('account.profile.googleAvailable', { email: profile.email })}</p>
      )}
      {profile.googleEmail && (
        <>
          <p className="text-ink-soft">
            {t('account.profile.googleConnected', { email: profile.googleEmail })}
          </p>
          {profile.hasPassword ? (
            <Button variant="secondary" disabled={disconnect.isPending} onClick={() => disconnect.mutate()}>
              {t('account.profile.googleDisconnect')}
            </Button>
          ) : (
            <p className="text-sm text-muted">{t('account.profile.googleNeedsPassword')}</p>
          )}
        </>
      )}
      {!profile.hasPassword && (
        <div className="space-y-4 border-t border-line pt-5">
          <p className="text-ink-soft">{t('account.profile.noPassword')}</p>
          {setPassword.isSuccess ? (
            <Alert tone="success">{t('account.profile.setPasswordSent', { email: profile.email })}</Alert>
          ) : (
            <Button
              variant="secondary"
              disabled={setPassword.isPending}
              onClick={() => setPassword.mutate(profile.email)}
            >
              {t('account.profile.setPassword')}
            </Button>
          )}
          {setPassword.isError && <Alert tone="error">{errorMessage(setPassword.error)}</Alert>}
        </div>
      )}
    </div>
  );
}
