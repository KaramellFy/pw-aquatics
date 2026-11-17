import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router';
import { GoogleButton } from '@/features/auth';
import { formatLocalTime } from '@/lib/format/date';

/**
 * Stands in for the password field for customers who sign in with Google: either the button that
 * confirms with Google (and comes back to this page) or, after that, until when the confirmation
 * counts. Whether it still counts is decided by the server (`confirmedUntil`).
 */
export function GoogleConfirmation({ confirmedUntil }: { confirmedUntil: string | null }) {
  const { t, i18n } = useTranslation();
  const { pathname } = useLocation();
  if (confirmedUntil) {
    return (
      <p className="text-sm text-ink-soft">
        {t('account.profile.confirmedUntil', { time: formatLocalTime(confirmedUntil, i18n.language) })}
      </p>
    );
  }
  return (
    <div className="space-y-3">
      <p className="text-sm text-ink-soft">{t('account.profile.confirmNeeded')}</p>
      <GoogleButton returnTo={pathname} intent="confirm" label={t('account.profile.confirmWithGoogle')} />
    </div>
  );
}
