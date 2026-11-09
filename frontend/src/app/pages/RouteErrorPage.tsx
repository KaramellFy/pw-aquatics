import { useTranslation } from 'react-i18next';
import { isRouteErrorResponse, useRouteError } from 'react-router';
import { Footer } from '@/components/layout/Footer';
import { Header } from '@/components/layout/Header';
import { Button, ButtonLink } from '@/components/ui/Button';
import { NotFoundPage } from './NotFoundPage';
import { StatusPage } from './StatusPage';

/** Top-level error boundary for the route tree. */
export function RouteErrorPage() {
  const { t } = useTranslation();
  const error = useRouteError();
  const notFound = isRouteErrorResponse(error) && error.status === 404;

  if (!notFound) console.error(error);

  return (
    <div className="flex min-h-dvh flex-col">
      <Header />
      <main id="main" className="flex-1">
        {notFound ? (
          <NotFoundPage />
        ) : (
          <StatusPage
            eyebrow={t('common.brand')}
            title={t('error.title')}
            text={t('error.text')}
            tone="stone"
            motif="waves"
            actions={
              <>
                <Button onClick={() => window.location.reload()}>{t('error.reload')}</Button>
                <ButtonLink to="/" variant="secondary">
                  {t('common.backToHome')}
                </ButtonLink>
              </>
            }
          />
        )}
      </main>
      <Footer />
    </div>
  );
}
