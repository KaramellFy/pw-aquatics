import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import { Reveal } from '@/components/ui/Reveal';
import type { GalleryItem } from '../data/gallery';
import { Lightbox } from './Lightbox';

export function GalleryGrid({ items }: { items: GalleryItem[] }) {
  const { t } = useTranslation();
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  return (
    <>
      <ul className="columns-1 gap-6 sm:columns-2 lg:columns-3">
        {items.map((item, index) => (
          <Reveal as="li" key={item.key} delay={(index % 3) * 80} className="mb-6 break-inside-avoid">
            <button
              type="button"
              onClick={() => setOpenIndex(index)}
              aria-label={t('gallery.openImage', { title: t(`gallery.items.${item.key}`) })}
              className="group block w-full overflow-hidden rounded-card text-left"
            >
              <Artwork
                tone={item.tone}
                motif={item.motif}
                className={`${item.aspect} w-full transition-transform duration-(--duration-slow) ease-(--ease-out-soft) group-hover:scale-[1.03]`}
              />
            </button>
            <p className="mt-3 text-sm text-muted">{t(`gallery.items.${item.key}`)}</p>
          </Reveal>
        ))}
      </ul>
      <Lightbox items={items} index={openIndex} onIndexChange={setOpenIndex} />
    </>
  );
}
