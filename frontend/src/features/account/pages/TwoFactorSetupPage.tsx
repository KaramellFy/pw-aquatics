import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { safeRedirectTarget } from '@/features/auth';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { useConfirmTwoFactorSetup, useStartTwoFactorSetup, useTwoFactorStatus } from '../api/twoFactor';
import { QrCode } from '../components/QrCode';
import { RecoveryCodesList } from '../components/RecoveryCodesList';
import { TwoFactorCodeForm } from '../components/TwoFactorCodeForm';

/**
 * Sets up two-step sign-in for an administrator: a new key as QR code, its first code to turn it
 * on, then the recovery codes (shown once). Administrators come here from the admin area until
 * it is set up (`?redirect=` brings them back), and from their profile to move it to a new phone.
 */
export function TwoFactorSetupPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const redirect = searchParams.get('redirect');
  const target = redirect ? safeRedirectTarget(redirect) : '/account/profile';
  const status = useTwoFactorStatus();
  const start = useStartTwoFactorSetup();
  const confirm = useConfirmTwoFactorSetup();
  const errorMessage = useApiErrorMessage();

  const codes = confirm.data?.recoveryCodes;
  const setup = start.data;

  return (
    <>
      <h1 className="text-headline">{t('account.twoFactor.setupTitle')}</h1>
      <div className="mt-10 max-w-xl space-y-10">
        {status.isPending && (
          <p role="status" className="text-muted">
            {t('common.loading')}
          </p>
        )}
        {status.isError && <Alert tone="error">{errorMessage(status.error)}</Alert>}

        {status.data && !setup && !codes && (
          <section className="space-y-5">
            <p className="text-ink-soft">{t('account.twoFactor.setupLead')}</p>
            {status.data.enabled ? (
              <TwoFactorCodeForm
                label={t('account.twoFactor.currentCode')}
                hint={t('account.twoFactor.replaceHint')}
                submitLabel={t('account.twoFactor.start')}
                allowRecovery
                isPending={start.isPending}
                error={start.error}
                onCode={(code) => start.mutate(code)}
              />
            ) : (
              <>
                {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
                <Button
                  aria-busy={start.isPending}
                  aria-disabled={start.isPending}
                  onClick={() => {
                    if (!start.isPending) start.mutate(undefined);
                  }}
                >
                  {start.isPending ? t('account.twoFactor.starting') : t('account.twoFactor.start')}
                </Button>
              </>
            )}
          </section>
        )}

        {setup && !codes && (
          <>
            <section className="space-y-4">
              <h2 className="text-title">{t('account.twoFactor.scanTitle')}</h2>
              <p className="text-ink-soft">{t('account.twoFactor.scanText')}</p>
              <QrCode
                value={setup.otpauthUri}
                label={t('account.twoFactor.qrLabel')}
                className="size-56 rounded-2xl border border-line"
              />
              <p className="text-sm text-ink-soft">{t('account.twoFactor.manual')}</p>
              <p translate="no" className="font-mono text-[0.9375rem] tracking-wide break-all text-ink">
                {setup.secret.match(/.{1,4}/g)?.join(' ')}
              </p>
            </section>
            <section className="space-y-4">
              <h2 className="text-title">{t('account.twoFactor.confirmTitle')}</h2>
              <TwoFactorCodeForm
                label={t('auth.twoFactor.code')}
                hint={t('account.twoFactor.confirmHint')}
                submitLabel={t('account.twoFactor.confirm')}
                isPending={confirm.isPending}
                error={confirm.error}
                onCode={(code) => confirm.mutate(code)}
              />
            </section>
          </>
        )}

        {codes && (
          <section className="space-y-5">
            <Alert tone="success">{t('account.twoFactor.enabled')}</Alert>
            <h2 className="text-title">{t('account.twoFactor.codesTitle')}</h2>
            <p className="text-ink-soft">{t('account.twoFactor.codesText')}</p>
            <RecoveryCodesList codes={codes} />
            <ButtonLink to={target} size="lg">
              {t('account.twoFactor.continue')}
            </ButtonLink>
          </section>
        )}
      </div>
    </>
  );
}
