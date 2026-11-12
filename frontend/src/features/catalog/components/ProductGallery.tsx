import { AnimatePresence, m, type PanInfo } from 'motion/react';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { cn } from '@/lib/cn';
import { duration, ease, exitDuration } from '@/lib/motion/tokens';
import type { ProductDetail } from '../api/catalog';
import { ProductImage } from './ProductImage';

type Image = ProductDetail['images'][number];

/** How far (px) or how fast (px/s) a swipe must go to change the photo. */
const SWIPE_DISTANCE = 60;
const SWIPE_SPEED = 400;

/**
 * Product photos: a large photo and thumbnails to choose from. The large photo can be swiped;
 * it follows the finger 1:1 while held, rubber-bands at the first and last photo, and the
 * old photo fades out while the new one fades in on top (both visible for a moment). The
 * thumbnail frame glides to the chosen photo.
 */
export function ProductGallery({
  images,
  productName,
  categorySlug,
  accentColor,
}: {
  images: Image[];
  productName: string;
  categorySlug?: string;
  accentColor?: string | null;
}) {
  const { t } = useTranslation();
  const [index, setIndex] = useState(0);
  const frame = useId();
  const current = images[index] ?? null;
  const several = images.length > 1;

  const go = (next: number) => setIndex(Math.max(0, Math.min(images.length - 1, next)));
  const onDragEnd = (_event: unknown, info: PanInfo) => {
    if (info.offset.x < -SWIPE_DISTANCE || info.velocity.x < -SWIPE_SPEED) go(index + 1);
    else if (info.offset.x > SWIPE_DISTANCE || info.velocity.x > SWIPE_SPEED) go(index - 1);
  };

  return (
    <div>
      <div className="relative grid aspect-[4/5] overflow-hidden rounded-card bg-mist sm:aspect-[5/4] lg:aspect-[4/5]">
        <AnimatePresence initial={false}>
          <m.div
            key={current?.url ?? 'placeholder'}
            className={cn(
              'col-start-1 row-start-1 size-full',
              several && 'cursor-grab touch-pan-y active:cursor-grabbing',
            )}
            initial={{ opacity: 0, scale: 1.02 }}
            animate={{ opacity: 1, scale: 1, transition: { duration: duration.slow, ease: ease.out } }}
            exit={{ opacity: 0, transition: { duration: exitDuration(duration.slow), ease: ease.out } }}
            drag={several ? 'x' : false}
            dragConstraints={{ left: 0, right: 0 }}
            dragElastic={0.18}
            dragSnapToOrigin
            onDragEnd={onDragEnd}
          >
            <ProductImage
              image={current}
              alt={current?.alt ?? productName}
              categorySlug={categorySlug}
              accentColor={accentColor}
              sizes="(min-width: 1024px) 55vw, 100vw"
              priority
              className="pointer-events-none select-none"
            />
          </m.div>
        </AnimatePresence>
        {several && (
          <p className="absolute right-3 bottom-3 rounded-full bg-surface/85 px-2.5 py-1 text-xs font-medium text-ink tabular-nums lg:hidden">
            {t('product.photoOf', { current: index + 1, total: images.length })}
          </p>
        )}
      </div>

      {several && (
        <ul className="mt-4 grid grid-cols-5 gap-3" aria-label={t('product.gallery')}>
          {images.map((image, position) => (
            <li key={image.url}>
              <button
                type="button"
                aria-pressed={position === index}
                aria-label={t('product.showPhoto', { number: position + 1 })}
                onClick={() => go(position)}
                className="relative block aspect-square w-full press rounded-lg p-1"
              >
                {position === index && (
                  <SlidingIndicator
                    layoutId={`thumb-${frame}`}
                    className="inset-0 rounded-lg"
                    markClassName="border-2 border-brand-700"
                  />
                )}
                <span className="block size-full overflow-hidden rounded-md bg-mist">
                  <ProductImage image={image} alt="" sizes="12vw" />
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
