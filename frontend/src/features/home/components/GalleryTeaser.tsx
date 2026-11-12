import { ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Artwork } from '@/components/ui/Artwork';
import { Container } from '@/components/ui/Container';
import { galleryItems } from '@/features/content';

/** Three pictures from the gallery; each wipes open as it scrolls into view. */
export function GalleryTeaser() {
  const { t } = useTranslation();
  const [first, second, third] = galleryItems;

  return (
    <section className="bg-ice py-20 sm:py-28">
      <Container>
        <div className="flex flex-wrap items-baseline justify-between gap-4">
          <h2 className="text-headline">{t('home.gallery.title')}</h2>
          <Link
            to="/gallery"
            className="group inline-flex items-center gap-2 text-[0.9375rem] font-medium text-brand-700"
          >
            {t('home.gallery.cta')}
            <ArrowRight
              aria-hidden
              className="size-4 transition-transform duration-(--duration-base) group-hover:translate-x-0.5"
            />
          </Link>
        </div>
        <div className="mt-12 grid gap-4 md:grid-cols-12">
          {first && (
            <figure className="md:col-span-7">
              <Artwork
                tone={first.tone}
                motif={first.motif}
                label={t(`gallery.items.${first.key}`)}
                className="aspect-[4/3] reveal-wipe rounded-card md:aspect-auto md:h-[calc(100%-2rem)]"
              />
              <figcaption className="mt-2 text-sm text-muted">{t(`gallery.items.${first.key}`)}</figcaption>
            </figure>
          )}
          <div className="grid gap-4 md:col-span-5">
            {[second, third].map(
              (item) =>
                item && (
                  <figure key={item.key}>
                    <Artwork
                      tone={item.tone}
                      motif={item.motif}
                      label={t(`gallery.items.${item.key}`)}
                      className="aspect-[4/3] reveal-wipe rounded-card"
                    />
                    <figcaption className="mt-2 text-sm text-muted">
                      {t(`gallery.items.${item.key}`)}
                    </figcaption>
                  </figure>
                ),
            )}
          </div>
        </div>
      </Container>
    </section>
  );
}
