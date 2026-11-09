import { useTranslation } from 'react-i18next';
import { BrandMark } from '@/components/layout/Brand';
import { ButtonLink } from '@/components/ui/Button';
import { Section } from '@/components/ui/Section';

export function ClosingCta() {
  const { t } = useTranslation();
  return (
    <Section spacing="compact">
      <div className="rounded-[2rem] border border-brand-100 bg-brand-50 px-6 py-16 text-center sm:px-12 sm:py-20">
        <BrandMark className="mx-auto h-14 w-auto text-brand-400" />
        <h2 className="mx-auto mt-8 max-w-2xl text-headline text-balance">{t('home.closing.title')}</h2>
        <p className="mt-5 text-lg text-ink-soft">{t('home.closing.text')}</p>
        <ButtonLink to="/contact" variant="accent" size="lg" className="mt-10">
          {t('home.closing.cta')}
        </ButtonLink>
      </div>
    </Section>
  );
}
