import { Microscope, ShieldCheck, Truck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Reveal } from '@/components/ui/Reveal';
import { Section, SectionHeading } from '@/components/ui/Section';

const values = [
  { key: 'health', Icon: ShieldCheck },
  { key: 'grading', Icon: Microscope },
  { key: 'shipping', Icon: Truck },
] as const;

export function ValuesSection() {
  const { t } = useTranslation();
  return (
    <Section tone="sand">
      <SectionHeading eyebrow={t('home.values.eyebrow')} title={t('home.values.title')} />
      <ul className="mt-16 grid gap-12 md:grid-cols-3 md:gap-10 lg:mt-20 lg:gap-16">
        {values.map(({ key, Icon }, index) => (
          <Reveal as="li" key={key} delay={index * 90} className="border-t border-ink/15 pt-8">
            <Icon aria-hidden className="size-6 text-moss-500" strokeWidth={1.4} />
            <h3 className="mt-6 text-2xl tracking-tight">{t(`home.values.items.${key}.title`)}</h3>
            <p className="mt-3 leading-relaxed text-ink/70">{t(`home.values.items.${key}.text`)}</p>
          </Reveal>
        ))}
      </ul>
    </Section>
  );
}
