import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { m } from 'motion/react';
import type { GalleryEntry } from '../data/galleryEntry';
import { GalleryVisual } from './GalleryVisual';
import { Lightbox } from './Lightbox';

export function GalleryGrid({ entries }: { entries: GalleryEntry[] }) {
  const { t } = useTranslation();
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  return (
    <>
      <ul className="columns-1 gap-6 sm:columns-2 lg:columns-3">
        {entries.map((entry, index) => (
          <li key={entry.key} className="mb-6 break-inside-avoid">
            <button
              type="button"
              onClick={() => setOpenIndex(index)}
              aria-label={t('gallery.openImage', { title: entry.title ?? entry.alt })}
              className="group block w-full press rounded-card text-left"
            >
              {/* Same layoutId as the large view: opening grows the picture out of this spot. */}
              <m.div
                layoutId={`gallery-${entry.key}`}
                className="reveal-wipe overflow-hidden rounded-card bg-mist"
                style={{ aspectRatio: entry.aspectRatio }}
              >
                <GalleryVisual
                  entry={entry}
                  sizes="(min-width: 1024px) 30vw, (min-width: 640px) 45vw, 92vw"
                  className="transition-transform duration-(--duration-slow) ease-(--ease-out) group-hover:scale-[1.03]"
                />
              </m.div>
            </button>
            {entry.title && <p className="mt-3 text-sm text-muted">{entry.title}</p>}
          </li>
        ))}
      </ul>
      <Lightbox entries={entries} index={openIndex} onIndexChange={setOpenIndex} />
    </>
  );
}
