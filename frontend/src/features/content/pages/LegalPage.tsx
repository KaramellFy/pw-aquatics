import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';
import { NotFoundPage } from '@/app/pages/NotFoundPage';
import { Alert } from '@/components/ui/Alert';
import { Container } from '@/components/ui/Container';
import { PageMeta } from '@/lib/seo/PageMeta';

const LEGAL_PAGES = ['imprint', 'privacy', 'terms', 'withdrawal', 'shipping'] as const;
type LegalPageKey = (typeof LEGAL_PAGES)[number];

function isLegalPage(value: string | undefined): value is LegalPageKey {
  return LEGAL_PAGES.some((page) => page === value);
}

/**
 * Legal page shells. Final texts must be written with legal advice before launch
 * (see docs/ARCHITECTURE.md R6) and will be managed as content.
 */
export function LegalPage() {
  const { t } = useTranslation();
  const { page } = useParams();

  if (!isLegalPage(page)) return <NotFoundPage />;

  const title = t(`legal.pages.${page}.title`);
  return (
    <>
      <PageMeta title={`${title} – ${t('common.brand')}`} noIndex />
      <Container className="max-w-3xl py-20 sm:py-28">
        <h1 className="text-headline">{title}</h1>
        <Alert className="mt-10">{t('legal.placeholder')}</Alert>
      </Container>
    </>
  );
}
