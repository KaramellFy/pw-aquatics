import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import { ButtonLink } from '@/components/ui/Button';
import { Reveal } from '@/components/ui/Reveal';
import { Section, SectionHeading } from '@/components/ui/Section';
import { galleryItems } from '@/features/content';

export function GalleryTeaser() {
  const { t } = useTranslation();
  const [first, second, third] = galleryItems;

  return (
    <Section tone="deep">
      <div className="flex flex-col gap-8 sm:flex-row sm:items-end sm:justify-between">
        <SectionHeading eyebrow={t('home.gallery.eyebrow')} title={t('home.gallery.title')} />
        <ButtonLink to="/gallery" variant="outline-light" className="group self-start sm:self-auto">
          {t('home.gallery.cta')}
          <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
        </ButtonLink>
      </div>
      <div className="mt-16 grid gap-5 md:grid-cols-12 lg:mt-20">
        {first && (
          <Reveal className="md:col-span-7">
            <Artwork
              tone={first.tone}
              motif={first.motif}
              label={t(`gallery.items.${first.key}`)}
              className="aspect-[4/3] rounded-card md:aspect-auto md:h-full"
            />
          </Reveal>
        )}
        <div className="grid gap-5 md:col-span-5">
          {[second, third].map(
            (item, index) =>
              item && (
                <Reveal key={item.key} delay={(index + 1) * 90}>
                  <Artwork
                    tone={item.tone}
                    motif={item.motif}
                    label={t(`gallery.items.${item.key}`)}
                    className="aspect-[4/3] rounded-card"
                  />
                  <p className="mt-3 text-sm text-paper/60">{t(`gallery.items.${item.key}`)}</p>
                </Reveal>
              ),
          )}
        </div>
      </div>
    </Section>
  );
}
