import { Artwork } from '@/components/ui/Artwork';
import { ResponsiveImage } from '@/components/ui/ResponsiveImage';
import { cn } from '@/lib/cn';
import type { GalleryEntry } from '../data/galleryEntry';

/** The picture of a gallery entry: the photo, or its artwork placeholder. */
export function GalleryVisual({
  entry,
  sizes,
  labelled = false,
  priority = false,
  className,
}: {
  entry: GalleryEntry;
  sizes: string;
  /** One of the first photos on screen: fetched first instead of lazily. */
  priority?: boolean;
  /** Give the picture its accessible name (in the large view; thumbnails are named by their button). */
  labelled?: boolean;
  className?: string;
}) {
  if (entry.photo) {
    return (
      <ResponsiveImage
        image={entry.photo}
        sizes={sizes}
        priority={priority}
        alt={labelled ? entry.alt : ''}
        className={cn('size-full object-cover', className)}
      />
    );
  }
  return (
    <Artwork
      tone={entry.artwork?.tone}
      motif={entry.artwork?.motif}
      label={labelled ? entry.alt : undefined}
      className={cn('size-full', className)}
    />
  );
}
