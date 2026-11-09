import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { PageMeta } from '@/lib/seo/PageMeta';
import { StatusPage } from './StatusPage';

type Section = 'shop' | 'cart' | 'account';

const visuals = {
  shop: { tone: 'deep', motif: 'shrimp' },
  cart: { tone: 'sand', motif: 'shell' },
  account: { tone: 'moss', motif: 'leaf' },
} as const;

/** Temporary page for sections that are built in later roadmap phases. */
function ComingSoon({ section }: { section: Section }) {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={`${t(`comingSoon.${section}.title`)} – ${t('common.brand')}`} noIndex />
      <StatusPage
        eyebrow={t('comingSoon.eyebrow')}
        title={t(`comingSoon.${section}.title`)}
        text={t(`comingSoon.${section}.text`)}
        {...visuals[section]}
        actions={
          <>
            <ButtonLink to="/gallery">{t('comingSoon.cta')}</ButtonLink>
            <ButtonLink to="/contact" variant="secondary">
              {t('nav.contact')}
            </ButtonLink>
          </>
        }
      />
    </>
  );
}

export const ShopComingSoonPage = () => <ComingSoon section="shop" />;
export const CartComingSoonPage = () => <ComingSoon section="cart" />;
export const AccountComingSoonPage = () => <ComingSoon section="account" />;
