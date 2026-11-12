import * as Dialog from '@radix-ui/react-dialog';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { AnimatePresence, m } from 'motion/react';
import { useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Artwork } from '@/components/ui/Artwork';
import { useReturnFocus } from '@/hooks/useReturnFocus';
import { duration, ease, exitDuration } from '@/lib/motion/tokens';
import type { GalleryItem } from '../data/gallery';

interface LightboxProps {
  items: GalleryItem[];
  index: number | null;
  onIndexChange: (index: number | null) => void;
}

const navButton =
  'press inline-flex size-12 items-center justify-center rounded-full border border-line-strong bg-surface text-ink transition-colors hover:bg-brand-50 active:bg-brand-100';

const fadeIn = { duration: duration.base, ease: ease.out, delay: 0.1 };
const fadeOut = { duration: exitDuration(duration.fast), ease: ease.out };

/**
 * Full-screen image viewer with keyboard (← →, Esc) navigation. The picture that was clicked
 * shares its layoutId with its thumbnail, so it grows out of the grid when opened and shrinks
 * back into its place when closed; caption and controls fade in around it. Moving to another
 * picture crossfades in place (old and new overlap) instead of flying in from the grid, and
 * focus returns to the thumbnail when the viewer closes.
 */
export function Lightbox({ items, index, onIndexChange }: LightboxProps) {
  const { t } = useTranslation();
  const returnFocus = useReturnFocus();
  const item = index === null ? undefined : items[index];
  const count = items.length;

  // Only the picture the viewer was opened with is linked to its thumbnail.
  const [opened, setOpened] = useState<{ open: boolean; morphKey: string | null }>({
    open: false,
    morphKey: null,
  });
  if (opened.open !== (item !== undefined)) {
    setOpened({ open: item !== undefined, morphKey: item?.key ?? null });
  }

  const go = (delta: number) => {
    if (index === null) return;
    setOpened((state) => ({ ...state, morphKey: null }));
    onIndexChange((index + delta + count) % count);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowRight') go(1);
    if (event.key === 'ArrowLeft') go(-1);
  };

  return (
    <Dialog.Root open={item !== undefined} onOpenChange={(open) => !open && onIndexChange(null)}>
      <AnimatePresence>
        {item && index !== null && (
          <Dialog.Portal forceMount>
            <Dialog.Overlay asChild forceMount>
              <m.div
                className="fixed inset-0 z-50 bg-surface/95"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1, transition: { duration: duration.base, ease: ease.out } }}
                exit={{ opacity: 0, transition: { duration: exitDuration(duration.base), ease: ease.out } }}
              />
            </Dialog.Overlay>
            <Dialog.Content
              forceMount
              onKeyDown={onKeyDown}
              {...returnFocus}
              className="fixed inset-0 z-50 flex flex-col p-4 text-ink focus:outline-none sm:p-8"
            >
              <m.div
                className="flex items-center justify-between"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1, transition: fadeIn }}
                exit={{ opacity: 0, transition: fadeOut }}
              >
                <p className="text-sm text-muted tabular-nums">
                  {t('gallery.counter', { current: index + 1, total: count })}
                </p>
                <Dialog.Close className={navButton} aria-label={t('common.close')}>
                  <X className="size-5" strokeWidth={1.5} />
                </Dialog.Close>
              </m.div>
              <figure className="flex min-h-0 flex-1 flex-col items-center justify-center gap-5 py-6">
                {/* One grid cell for all pictures, so the outgoing and incoming ones overlap. */}
                <div className="grid min-h-0 w-full flex-1 grid-rows-[minmax(0,1fr)] place-items-center">
                  {/* propagate: the picture also fades when the whole viewer closes. */}
                  <AnimatePresence initial={false} propagate>
                    <m.div
                      key={item.key}
                      layoutId={item.key === opened.morphKey ? `gallery-${item.key}` : undefined}
                      initial={{ opacity: 0, scale: 0.98 }}
                      animate={{
                        opacity: 1,
                        scale: 1,
                        transition: { duration: duration.base, ease: ease.out },
                      }}
                      exit={{
                        opacity: 0,
                        transition: { duration: exitDuration(duration.base), ease: ease.out },
                      }}
                      transition={{ layout: { duration: 0.4, ease: ease.inOut } }}
                      className={`${item.aspect} [height:min(70vh,56rem)] max-h-full w-auto max-w-full overflow-hidden rounded-card [grid-area:1/1]`}
                    >
                      <Artwork
                        tone={item.tone}
                        motif={item.motif}
                        label={t(`gallery.items.${item.key}`)}
                        className="size-full"
                      />
                    </m.div>
                  </AnimatePresence>
                </div>
                <Dialog.Title asChild>
                  <m.figcaption
                    className="font-display text-xl"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1, transition: fadeIn }}
                    exit={{ opacity: 0, transition: fadeOut }}
                  >
                    {t(`gallery.items.${item.key}`)}
                  </m.figcaption>
                </Dialog.Title>
              </figure>
              <Dialog.Description className="sr-only">{t('gallery.lead')}</Dialog.Description>
              <m.div
                className="flex justify-center gap-3"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1, transition: fadeIn }}
                exit={{ opacity: 0, transition: fadeOut }}
              >
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
              </m.div>
            </Dialog.Content>
          </Dialog.Portal>
        )}
      </AnimatePresence>
    </Dialog.Root>
  );
}
