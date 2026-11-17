import { useTranslation } from 'react-i18next';
import { useSignInOptions } from '../api/auth';
import { GoogleButton } from './GoogleButton';

/**
 * "Continue with Google" above the email form of the sign-in and registration pages, with an
 * "or" between them. Shows nothing while the shop does not offer Google.
 */
export function GoogleSignIn({ returnTo }: { returnTo: string }) {
  const { t } = useTranslation();
  const options = useSignInOptions();
  if (!options.data?.google) {
    return null;
  }
  return (
    <div className="mb-7">
      <GoogleButton returnTo={returnTo} />
      <p className="mt-3 text-center text-sm text-muted">{t('auth.google.hint')}</p>
      <div className="mt-7 flex items-center gap-4 text-sm text-muted" aria-hidden>
        <span className="h-px flex-1 bg-line" />
        {t('auth.google.or')}
        <span className="h-px flex-1 bg-line" />
      </div>
    </div>
  );
}
