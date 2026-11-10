import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { PageMeta } from '@/lib/seo/PageMeta';
import { StatusPage } from './StatusPage';

/** Temporary cart page until the cart is built (Phase 4). */
export function CartComingSoonPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={`${t('comingSoon.cart.title')} – ${t('common.brand')}`} noIndex />
      <StatusPage
        eyebrow={t('comingSoon.eyebrow')}
        title={t('comingSoon.cart.title')}
        text={t('comingSoon.cart.text')}
        tone="ice"
        motif="shell"
        actions={
          <>
            <ButtonLink to="/shop">{t('nav.shop')}</ButtonLink>
            <ButtonLink to="/contact" variant="secondary">
              {t('nav.contact')}
            </ButtonLink>
          </>
        }
      />
    </>
  );
}
