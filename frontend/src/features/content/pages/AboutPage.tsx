import { HeartHandshake, Microscope, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Artwork } from '@/components/ui/Artwork';
import { Container } from '@/components/ui/Container';
import { Reveal } from '@/components/ui/Reveal';
import { Section, SectionHeading } from '@/components/ui/Section';
import { PageMeta } from '@/lib/seo/PageMeta';

const principles = [
  { key: 'care', Icon: HeartHandshake },
  { key: 'transparency', Icon: Microscope },
  { key: 'responsibility', Icon: ShieldCheck },
] as const;

export function AboutPage() {
  const { t } = useTranslation();
  const paragraphs = t('about.story.paragraphs', { returnObjects: true });

  return (
    <>
      <PageMeta title={t('about.meta.title')} description={t('about.meta.description')} />

      <Section spacing="compact">
        <SectionHeading
          as="h1"
          size="display"
          eyebrow={t('about.eyebrow')}
          title={t('about.title')}
          lead={t('about.lead')}
        />
      </Section>

      <div className="px-3 sm:px-5">
        <Artwork
          tone="sky"
          motif="brand"
          animated
          className="aspect-[16/10] rounded-[2rem] sm:aspect-[21/9]"
        />
      </div>

      <Section>
        <div className="grid gap-10 lg:grid-cols-12">
          <h2 className="text-headline lg:col-span-4">{t('about.story.title')}</h2>
          <div className="space-y-6 text-lg leading-relaxed text-ink-soft lg:col-span-7 lg:col-start-6">
            {paragraphs.map((paragraph) => (
              <Reveal key={paragraph}>
                <p>{paragraph}</p>
              </Reveal>
            ))}
          </div>
        </div>
      </Section>

      <Section tone="mist">
        <SectionHeading title={t('about.principles.title')} />
        <ul className="mt-16 grid gap-12 md:grid-cols-3 md:gap-10 lg:gap-16">
          {principles.map(({ key, Icon }, index) => (
            <Reveal as="li" key={key} delay={index * 90} className="border-t border-brand-200 pt-8">
              <Icon aria-hidden className="size-6 text-brand-700" strokeWidth={1.4} />
              <h3 className="mt-6 text-2xl tracking-tight">{t(`about.principles.items.${key}.title`)}</h3>
              <p className="mt-3 leading-relaxed text-ink-soft">{t(`about.principles.items.${key}.text`)}</p>
            </Reveal>
          ))}
        </ul>
      </Section>

      <Container className="py-16">
        <Alert>{t('about.note')}</Alert>
      </Container>
    </>
  );
}
