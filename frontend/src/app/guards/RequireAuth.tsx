import { useTranslation } from 'react-i18next';
import { Navigate, Outlet, useLocation } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';
import { loginPathFor, useSession } from '@/features/auth';

/**
 * Shows the nested routes only to signed-in users; everyone else is sent to the login
 * page and returned here afterwards. This is a convenience for the UI only — the API
 * enforces access on every request.
 */
export function RequireAuth() {
  const { t } = useTranslation();
  const location = useLocation();
  const session = useSession();

  if (session.isPending) {
    return (
      <Container className="py-24" aria-busy>
        <p role="status" className="text-muted">
          {t('common.loading')}
        </p>
      </Container>
    );
  }
  if (session.isError && !session.data) {
    return (
      <Container className="max-w-xl py-24">
        <Alert tone="error">
          <p>{t('apiErrors.NETWORK_ERROR')}</p>
          <Button variant="secondary" size="sm" className="mt-4" onClick={() => void session.refetch()}>
            {t('shop.error.retry')}
          </Button>
        </Alert>
      </Container>
    );
  }
  if (!session.data?.authenticated) {
    return <Navigate to={loginPathFor(location.pathname, location.search)} replace />;
  }
  return <Outlet />;
}
