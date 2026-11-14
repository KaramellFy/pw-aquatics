import { Artwork } from '@/components/ui/Artwork';
import { ResponsiveImage } from '@/components/ui/ResponsiveImage';
import { cn } from '@/lib/cn';
import type { GalleryEntry } from '../data/galleryEntry';

/** The picture of a gallery entry: the photo, or its artwork placeholder. */
export function GalleryVisual({
  entry,
  sizes,
  labelled = false,
  className,
}: {
  entry: GalleryEntry;
  sizes: string;
  /** Give the picture its accessible name (in the large view; thumbnails are named by their button). */
  labelled?: boolean;
  className?: string;
}) {
  if (entry.photo) {
    return (
      <ResponsiveImage
        image={entry.photo}
        sizes={sizes}
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
