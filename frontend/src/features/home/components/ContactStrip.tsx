import { useTranslation } from 'react-i18next';
import { ButtonLink } from '@/components/ui/Button';
import { Container } from '@/components/ui/Container';

/** A plain closing line before the footer: who to ask, and a way to do it. */
export function ContactStrip() {
  const { t } = useTranslation();
  return (
    <section className="py-16 sm:py-20">
      <Container>
        <div className="grid gap-8 border-t border-line pt-12 sm:pt-16 lg:grid-cols-12 lg:items-end">
          <div className="lg:col-span-8">
            <h2 className="text-title text-balance">{t('home.contact.title')}</h2>
            <p className="mt-3 max-w-2xl leading-relaxed text-ink-soft">{t('home.contact.text')}</p>
          </div>
          <div className="lg:col-span-4 lg:justify-self-end">
            <ButtonLink to="/contact" variant="secondary" size="lg">
              {t('home.contact.cta')}
            </ButtonLink>
          </div>
        </div>
      </Container>
    </section>
  );
}
