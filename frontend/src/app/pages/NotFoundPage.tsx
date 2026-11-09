import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { PageMeta } from '@/lib/seo/PageMeta';
import { StatusPage } from './StatusPage';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={t('notFound.meta.title')} noIndex />
      <StatusPage
        eyebrow={t('notFound.eyebrow')}
        title={t('notFound.title')}
        text={t('notFound.text')}
        tone="night"
        motif="fish"
        actions={<ButtonLink to="/">{t('common.backToHome')}</ButtonLink>}
      />
    </>
  );
}
