import { useTranslation } from 'react-i18next';
import { Section, SectionHeading } from '@/components/ui/Section';
import { PageMeta } from '@/lib/seo/PageMeta';
import { GalleryGrid } from '../components/GalleryGrid';
import { galleryItems } from '../data/gallery';

export function GalleryPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={t('gallery.meta.title')} description={t('gallery.meta.description')} />
      <Section spacing="compact" className="pb-0 sm:pb-0 lg:pb-0">
        <SectionHeading as="h1" size="display" title={t('gallery.title')} lead={t('gallery.lead')} />
      </Section>
      <Section spacing="compact">
        <GalleryGrid items={galleryItems} />
      </Section>
    </>
  );
}
