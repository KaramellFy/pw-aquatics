import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import { m } from 'motion/react';
import type { GalleryItem } from '../data/gallery';
import { Lightbox } from './Lightbox';

export function GalleryGrid({ items }: { items: GalleryItem[] }) {
  const { t } = useTranslation();
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  return (
    <>
      <ul className="columns-1 gap-6 sm:columns-2 lg:columns-3">
        {items.map((item, index) => (
          <li key={item.key} className="mb-6 break-inside-avoid">
            <button
              type="button"
              onClick={() => setOpenIndex(index)}
              aria-label={t('gallery.openImage', { title: t(`gallery.items.${item.key}`) })}
              className="group block w-full press rounded-card text-left"
            >
              {/* Same layoutId as the large view: opening grows the picture out of this spot. */}
              <m.div layoutId={`gallery-${item.key}`} className="reveal-wipe overflow-hidden rounded-card">
                <Artwork
                  tone={item.tone}
                  motif={item.motif}
                  className={`${item.aspect} w-full transition-transform duration-(--duration-slow) ease-(--ease-out) group-hover:scale-[1.03]`}
                />
              </m.div>
            </button>
            <p className="mt-3 text-sm text-muted">{t(`gallery.items.${item.key}`)}</p>
          </li>
        ))}
      </ul>
      <Lightbox items={items} index={openIndex} onIndexChange={setOpenIndex} />
    </>
  );
}
