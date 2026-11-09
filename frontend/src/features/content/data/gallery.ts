import type { ArtworkMotif, ArtworkTone } from '@/components/ui/Artwork';
import type { Messages } from '@/lib/i18n/locales/en';

export type GalleryItemKey = keyof Messages['gallery']['items'];

export interface GalleryItem {
  key: GalleryItemKey;
  tone: ArtworkTone;
  motif: ArtworkMotif;
  /** Tailwind aspect-ratio class; varied ratios create the masonry rhythm. */
  aspect: string;
}

/**
 * TEMPORARY static gallery. In Phase 7 this is replaced by `GET /api/v1/gallery`
 * (managed in the admin panel) and the placeholders by real photography.
 */
export const galleryItems: GalleryItem[] = [
  { key: 'blueDream', tone: 'lagoon', motif: 'shrimp', aspect: 'aspect-[4/5]' },
  { key: 'moss', tone: 'ice', motif: 'leaf', aspect: 'aspect-[4/3]' },
  { key: 'redFire', tone: 'sky', motif: 'shrimp', aspect: 'aspect-[4/3]' },
  { key: 'racingSnail', tone: 'pearl', motif: 'snail', aspect: 'aspect-square' },
  { key: 'crystalRed', tone: 'deep', motif: 'shrimp', aspect: 'aspect-[3/4]' },
  { key: 'dwarfCrayfish', tone: 'aqua', motif: 'shell', aspect: 'aspect-[4/5]' },
  { key: 'yellowFire', tone: 'ice', motif: 'shrimp', aspect: 'aspect-[4/3]' },
  { key: 'tigerShrimp', tone: 'lagoon', motif: 'shrimp', aspect: 'aspect-square' },
  { key: 'ramshornSnail', tone: 'sky', motif: 'snail', aspect: 'aspect-[4/5]' },
];
