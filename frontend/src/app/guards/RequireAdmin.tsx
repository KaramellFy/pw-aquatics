import { Navigate, Outlet, useLocation } from 'react-router';
import { Container } from '@/components/ui/Container';
import { loginPathFor, useSession } from '@/features/auth';
import { NotFoundPage } from '../pages/NotFoundPage';
import { useTranslation } from 'react-i18next';

/**
 * Shows the admin area only to administrators. Visitors who are not signed in go to the
 * login page; signed-in customers see "not found", so the admin area is not advertised.
 * This is a convenience for the UI only — every admin endpoint checks the role itself.
 */
export function RequireAdmin() {
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
  if (!session.data?.authenticated) {
    return <Navigate to={loginPathFor(location.pathname, location.search)} replace />;
  }
  if (session.data.user?.role !== 'ADMIN') {
    return <NotFoundPage />;
  }
  return <Outlet />;
}
