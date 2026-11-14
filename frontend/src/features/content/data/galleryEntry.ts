import type { ArtworkMotif, ArtworkTone } from '@/components/ui/Artwork';
import type { ImageSource } from '@/components/ui/ResponsiveImage';

/**
 * One picture of the gallery page, either a published photo from the API or (until the owner
 * publishes photos) an artwork placeholder.
 */
export interface GalleryEntry {
  key: string;
  /** Shown under the picture and in the large view; may be empty for photos without a title. */
  title: string | null;
  caption: string | null;
  /** Accessible name of the picture. */
  alt: string;
  /** Width ÷ height, so the picture takes its space before it loads. */
  aspectRatio: number;
  photo?: ImageSource;
  artwork?: { tone: ArtworkTone; motif: ArtworkMotif };
}
