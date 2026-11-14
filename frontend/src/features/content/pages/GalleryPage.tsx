import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Section, SectionHeading } from '@/components/ui/Section';
import { PageMeta } from '@/lib/seo/PageMeta';
import { usePublishedGallery } from '../api/gallery';
import { GalleryGrid } from '../components/GalleryGrid';
import { galleryItems, placeholderRatio } from '../data/gallery';
import type { GalleryEntry } from '../data/galleryEntry';

/** The gallery: published photos from the admin area, or artwork placeholders until there are any. */
export function GalleryPage() {
  const { t } = useTranslation();
  const gallery = usePublishedGallery();
  const photos: GalleryEntry[] = (gallery.data?.pages ?? []).flatMap((page) =>
    page.items.map((image) => ({
      key: image.id,
      title: image.title,
      caption: image.caption,
      alt: image.image.alt,
      aspectRatio: image.image.width / image.image.height,
      photo: image.image,
    })),
  );
  // Until the owner publishes photos (or if they cannot be loaded), the placeholders stand in.
  const showPlaceholders = gallery.isError || (gallery.isSuccess && photos.length === 0);
  const entries: GalleryEntry[] = showPlaceholders
    ? galleryItems.map((item) => {
        const title = t(`gallery.items.${item.key}`);
        return {
          key: item.key,
          title,
          caption: null,
          alt: title,
          aspectRatio: placeholderRatio(item),
          artwork: { tone: item.tone, motif: item.motif },
        };
      })
    : photos;

  return (
    <>
      <PageMeta title={t('gallery.meta.title')} description={t('gallery.meta.description')} />
      <Section spacing="compact" className="pb-0 sm:pb-0 lg:pb-0">
        <SectionHeading as="h1" size="display" title={t('gallery.title')} lead={t('gallery.lead')} />
      </Section>
      <Section spacing="compact">
        {gallery.isPending && (
          <p role="status" className="text-muted">
            {t('common.loading')}
          </p>
        )}
        {entries.length > 0 && <GalleryGrid entries={entries} />}
        {!showPlaceholders && gallery.hasNextPage && (
          <div className="mt-4 flex justify-center">
            <Button
              variant="secondary"
              aria-busy={gallery.isFetchingNextPage || undefined}
              onClick={() => !gallery.isFetchingNextPage && void gallery.fetchNextPage()}
            >
              {t('gallery.more')}
            </Button>
          </div>
        )}
      </Section>
    </>
  );
}
