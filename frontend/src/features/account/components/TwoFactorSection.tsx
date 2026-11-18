import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, ButtonLink } from '@/components/ui/Button';
import { useApiErrorMessage } from '@/lib/api/useApiErrorMessage';
import { formatInstantDate } from '@/lib/format/date';
import { useRenewRecoveryCodes, useTwoFactorStatus } from '../api/twoFactor';
import { RecoveryCodesList } from './RecoveryCodesList';
import { TwoFactorCodeForm } from './TwoFactorCodeForm';

/** Two-step sign-in on an administrator's profile: status, new app, new recovery codes. */
export function TwoFactorSection() {
  const { t, i18n } = useTranslation();
  const status = useTwoFactorStatus();
  const renew = useRenewRecoveryCodes();
  const errorMessage = useApiErrorMessage();
  const [renewing, setRenewing] = useState(false);

  if (status.isPending) {
    return (
      <p role="status" className="text-muted">
        {t('common.loading')}
      </p>
    );
  }
  if (status.isError) {
    return <Alert tone="error">{errorMessage(status.error)}</Alert>;
  }
  if (!status.data.enabled) {
    return (
      <div className="space-y-5">
        <p className="text-ink-soft">{t('account.twoFactor.off')}</p>
        <ButtonLink to="/account/two-factor">{t('account.twoFactor.setUp')}</ButtonLink>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <p className="text-ink-soft">
        {status.data.enabledAt &&
          t('account.twoFactor.on', { date: formatInstantDate(status.data.enabledAt, i18n.language) })}{' '}
        {t('account.twoFactor.codesLeft', { count: status.data.recoveryCodesLeft })}
      </p>
      {renew.data ? (
        <>
          <Alert tone="success">{t('account.twoFactor.newCodesDone')}</Alert>
          <RecoveryCodesList codes={renew.data.recoveryCodes} />
        </>
      ) : renewing ? (
        <div className="space-y-4 border-t border-line pt-5">
          <p className="text-ink-soft">{t('account.twoFactor.newCodesLead')}</p>
          <TwoFactorCodeForm
            label={t('account.twoFactor.currentCode')}
            submitLabel={t('account.twoFactor.newCodesSubmit')}
            allowRecovery
            isPending={renew.isPending}
            error={renew.error}
            onCode={(code) => renew.mutate(code)}
          />
        </div>
      ) : (
        <div className="flex flex-wrap gap-3">
          <ButtonLink to="/account/two-factor" variant="secondary">
            {t('account.twoFactor.newApp')}
          </ButtonLink>
          <Button variant="secondary" onClick={() => setRenewing(true)}>
            {t('account.twoFactor.newCodes')}
          </Button>
        </div>
      )}
    </div>
  );
}
