import { Artwork, type ArtworkMotif, type ArtworkTone } from '@/components/ui/Artwork';
import { ResponsiveImage } from '@/components/ui/ResponsiveImage';
import { cn } from '@/lib/cn';
import type { Schemas } from '@/lib/api/types';

type Image = Schemas['ImageView'];

const motifByCategory: Record<string, ArtworkMotif> = {
  shrimp: 'shrimp',
  neocaridina: 'shrimp',
  caridina: 'shrimp',
  snails: 'snail',
  crayfish: 'shell',
  crabs: 'waves',
};

interface ProductImageProps {
  image: Image | null;
  /** Shown on hover (cards only). */
  hoverImage?: Image | null;
  alt: string;
  categorySlug?: string;
  accentColor?: string | null;
  sizes: string;
  priority?: boolean;
  className?: string;
  tone?: ArtworkTone;
}

/** A product photo, or an art-directed placeholder until photography exists. */
export function ProductImage({
  image,
  hoverImage,
  alt,
  categorySlug,
  accentColor,
  sizes,
  priority,
  className,
  tone = 'ice',
}: ProductImageProps) {
  if (!image) {
    return (
      <Artwork
        tone={tone}
        motif={motifByCategory[categorySlug ?? ''] ?? 'shrimp'}
        accentColor={accentColor}
        label={alt}
        className={cn('size-full', className)}
      />
    );
  }
  return (
    <div className={cn('relative size-full', className)}>
      <ResponsiveImage image={image} sizes={sizes} priority={priority} className="size-full" />
      {hoverImage && (
        <ResponsiveImage
          image={hoverImage}
          sizes={sizes}
          alt=""
          className="absolute inset-0 size-full opacity-0 transition-opacity duration-(--duration-slow) group-hover:opacity-100"
        />
      )}
    </div>
  );
}
