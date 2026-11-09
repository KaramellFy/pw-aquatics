import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { Section } from '@/components/ui/Section';

export function ClosingCta() {
  const { t } = useTranslation();
  return (
    <Section spacing="compact" className="text-center">
      <div className="mx-auto max-w-2xl py-8">
        <h2 className="text-headline text-balance">{t('home.closing.title')}</h2>
        <p className="mt-5 text-lg text-ink/70">{t('home.closing.text')}</p>
        <ButtonLink to="/contact" size="lg" className="mt-10">
          {t('home.closing.cta')}
        </ButtonLink>
      </div>
    </Section>
  );
}
