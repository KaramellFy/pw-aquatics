import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Artwork } from '@/components/ui/Artwork';
import { Container } from '@/components/ui/Container';
import { MaskedWords } from '@/components/motion/MaskedWords';
import { PageMeta } from '@/lib/seo/PageMeta';

const principles = ['care', 'transparency', 'responsibility'] as const;

export function AboutPage() {
  const { t } = useTranslation();
  const paragraphs = t('about.story.paragraphs', { returnObjects: true });

  return (
    <>
      <PageMeta title={t('about.meta.title')} description={t('about.meta.description')} />

      <Container className="pt-12 pb-14 sm:pt-16 lg:pt-20">
        <div className="max-w-4xl">
          <h1 className="text-display text-balance">
            <MaskedWords text={t('about.title')} />
          </h1>
          <p className="mt-8 max-w-2xl animate-enter text-lg leading-relaxed text-pretty text-ink-soft [animation-delay:300ms]">
            {t('about.lead')}
          </p>
        </div>
      </Container>

      <Container>
        <Artwork
          tone="sky"
          motif="brand"
          className="aspect-[16/10] animate-[wipe_1000ms_var(--ease-out)_250ms_both] rounded-card sm:aspect-[21/9]"
        />
      </Container>

      <Container className="grid gap-10 py-20 sm:py-28 lg:grid-cols-12">
        <h2 className="text-headline lg:col-span-4">{t('about.story.title')}</h2>
        <div className="space-y-6 text-lg leading-relaxed text-pretty text-ink-soft lg:col-span-7 lg:col-start-6">
          {paragraphs.map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
        </div>
      </Container>

      <section className="bg-mist py-20 sm:py-28">
        <Container className="grid gap-10 lg:grid-cols-12">
          <h2 className="text-headline lg:col-span-4">{t('about.principles.title')}</h2>
          <dl className="divide-y divide-line border-y border-line lg:col-span-7 lg:col-start-6">
            {principles.map((key) => (
              <div key={key} className="grid gap-2 py-6 sm:grid-cols-[12rem_1fr] sm:gap-8">
                <dt className="font-medium text-ink">{t(`about.principles.items.${key}.title`)}</dt>
                <dd className="leading-relaxed text-ink-soft">{t(`about.principles.items.${key}.text`)}</dd>
              </div>
            ))}
          </dl>
        </Container>
      </section>

      <Container className="py-16">
        <Alert>{t('about.note')}</Alert>
      </Container>
    </>
  );
}
