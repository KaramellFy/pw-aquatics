import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Container } from '@/components/ui/Container';
import { LanguageSwitcher } from './LanguageSwitcher';
import { Logo } from './Logo';
import { categoryLinks, legalNavigation } from './navigation';

const linkClass = 'text-paper/65 transition-colors hover:text-paper';

export function Footer() {
  const { t } = useTranslation();
  const year = new Date().getFullYear();

  return (
    <footer className="bg-deep-900 text-paper">
      <Container className="grid gap-14 py-20 lg:grid-cols-12 lg:py-24">
        <div className="lg:col-span-4">
          <Logo inverted />
          <p className="mt-6 max-w-xs leading-relaxed text-paper/65">{t('footer.tagline')}</p>
        </div>

        <nav aria-label={t('footer.shopTitle')} className="lg:col-span-2 lg:col-start-6">
          <h2 className="mb-5 font-sans eyebrow text-paper/45">{t('footer.shopTitle')}</h2>
          <ul className="space-y-3 text-[0.9375rem]">
            <li>
              <Link to="/shop" className={linkClass}>
                {t('footer.allAnimals')}
              </Link>
            </li>
            {categoryLinks.map((category) => (
              <li key={category.slug}>
                <Link to={`/shop?category=${category.slug}`} className={linkClass}>
                  {t(`categories.${category.key}.name`)}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav aria-label={t('footer.infoTitle')} className="lg:col-span-2">
          <h2 className="mb-5 font-sans eyebrow text-paper/45">{t('footer.infoTitle')}</h2>
          <ul className="space-y-3 text-[0.9375rem]">
            <li>
              <Link to="/about" className={linkClass}>
                {t('nav.about')}
              </Link>
            </li>
            <li>
              <Link to="/gallery" className={linkClass}>
                {t('nav.gallery')}
              </Link>
            </li>
            <li>
              <Link to="/contact" className={linkClass}>
                {t('nav.contact')}
              </Link>
            </li>
            <li>
              <Link to="/legal/versand" className={linkClass}>
                {t('footer.shipping')}
              </Link>
            </li>
          </ul>
        </nav>

        <nav aria-label={t('footer.legalTitle')} className="lg:col-span-3">
          <h2 className="mb-5 font-sans eyebrow text-paper/45">{t('footer.legalTitle')}</h2>
          <ul className="space-y-3 text-[0.9375rem]">
            {legalNavigation.map((item) => (
              <li key={item.to}>
                <Link to={item.to} className={linkClass}>
                  {t(item.labelKey)}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </Container>

      <div className="border-t border-paper/10">
        <Container className="flex flex-col gap-4 py-6 text-sm text-paper/50 sm:flex-row sm:items-center sm:justify-between">
          <p>
            {t('footer.copyright', { year })} <span className="sm:ml-2">{t('footer.vatNote')}</span>
          </p>
          <LanguageSwitcher className="text-paper" />
        </Container>
      </div>
    </footer>
  );
}
