import * as Dialog from '@radix-ui/react-dialog';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import type { KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import type { GalleryItem } from '../data/gallery';

interface LightboxProps {
  items: GalleryItem[];
  index: number | null;
  onIndexChange: (index: number | null) => void;
}

const navButton =
  'inline-flex size-12 items-center justify-center rounded-full border border-paper/20 text-paper transition-colors hover:bg-paper/10';

/** Full-screen image viewer with keyboard (← →, Esc) navigation. */
export function Lightbox({ items, index, onIndexChange }: LightboxProps) {
  const { t } = useTranslation();
  const item = index === null ? undefined : items[index];
  const count = items.length;

  const go = (delta: number) => {
    if (index === null) return;
    onIndexChange((index + delta + count) % count);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowRight') go(1);
    if (event.key === 'ArrowLeft') go(-1);
  };

  return (
    <Dialog.Root open={item !== undefined} onOpenChange={(open) => !open && onIndexChange(null)}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-deep-900/95 backdrop-blur-sm" />
        <Dialog.Content
          onKeyDown={onKeyDown}
          className="fixed inset-0 z-50 flex flex-col p-4 text-paper focus:outline-none sm:p-8"
        >
          {item && index !== null && (
            <>
              <div className="flex items-center justify-between">
                <p className="text-sm text-paper/60 tabular-nums">
                  {t('gallery.counter', { current: index + 1, total: count })}
                </p>
                <Dialog.Close className={navButton} aria-label={t('common.close')}>
                  <X className="size-5" strokeWidth={1.5} />
                </Dialog.Close>
              </div>
              <figure className="flex min-h-0 flex-1 flex-col items-center justify-center gap-5 py-6">
                <Artwork
                  key={item.key}
                  tone={item.tone}
                  motif={item.motif}
                  label={t(`gallery.items.${item.key}`)}
                  className={`${item.aspect} [height:min(70vh,56rem)] max-h-full w-auto max-w-full animate-fade-up rounded-card`}
                />
                <Dialog.Title asChild>
                  <figcaption className="font-serif text-xl">{t(`gallery.items.${item.key}`)}</figcaption>
                </Dialog.Title>
              </figure>
              <Dialog.Description className="sr-only">{t('gallery.lead')}</Dialog.Description>
              <div className="flex justify-center gap-3">
                <button
                  type="button"
                  className={navButton}
                  onClick={() => go(-1)}
                  aria-label={t('gallery.previous')}
                >
                  <ChevronLeft className="size-5" strokeWidth={1.5} />
                </button>
                <button
                  type="button"
                  className={navButton}
                  onClick={() => go(1)}
                  aria-label={t('gallery.next')}
                >
                  <ChevronRight className="size-5" strokeWidth={1.5} />
                </button>
              </div>
            </>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
