import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import { Reveal } from '@/components/ui/Reveal';
import { Section, SectionHeading } from '@/components/ui/Section';

const steps = ['select', 'acclimate', 'pack', 'ship'] as const;

export function ProcessSection() {
  const { t } = useTranslation();
  return (
    <Section>
      <div className="grid gap-16 lg:grid-cols-12 lg:gap-12">
        <div className="lg:sticky lg:top-32 lg:col-span-5 lg:self-start">
          <SectionHeading eyebrow={t('home.process.eyebrow')} title={t('home.process.title')} />
          <Artwork tone="moss" motif="leaf" className="mt-12 hidden aspect-[4/3] rounded-card lg:block" />
        </div>
        <ol className="lg:col-span-6 lg:col-start-7">
          {steps.map((step, index) => (
            <Reveal
              as="li"
              key={step}
              className="grid grid-cols-[4rem_1fr] gap-4 border-t border-ink/12 py-10 first:border-t-0 first:pt-0 sm:grid-cols-[6rem_1fr] lg:first:pt-2"
            >
              <span className="font-serif text-3xl text-moss-500/70 tabular-nums" aria-hidden>
                {String(index + 1).padStart(2, '0')}
              </span>
              <div>
                <h3 className="text-2xl tracking-tight">{t(`home.process.steps.${step}.title`)}</h3>
                <p className="mt-3 max-w-md leading-relaxed text-ink/70">
                  {t(`home.process.steps.${step}.text`)}
                </p>
              </div>
            </Reveal>
          ))}
        </ol>
      </div>
    </Section>
  );
}
