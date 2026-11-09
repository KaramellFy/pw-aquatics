import type { ComponentProps } from 'react';
import { cn } from '@/lib/cn';

export interface ImageSource {
  url: string;
  srcSet?: string;
  alt: string;
  width: number;
  height: number;
}

type ResponsiveImageProps = Omit<ComponentProps<'img'>, 'src' | 'srcSet' | 'alt' | 'width' | 'height'> & {
  image: ImageSource;
  /** The `sizes` attribute describing the rendered width, e.g. "(min-width: 1024px) 25vw, 50vw". */
  sizes: string;
  /** Above-the-fold images load eagerly with high priority. */
  priority?: boolean;
  /** Overrides the image's alt text, e.g. "" for a decorative duplicate. */
  alt?: string;
};

/**
 * Images rendered from backend-generated WebP renditions. Explicit width/height
 * prevent layout shift; images below the fold are lazy-loaded.
 */
export function ResponsiveImage({
  image,
  sizes,
  priority = false,
  alt,
  className,
  ...props
}: ResponsiveImageProps) {
  return (
    <img
      src={image.url}
      srcSet={image.srcSet}
      sizes={image.srcSet ? sizes : undefined}
      alt={alt ?? image.alt}
      width={image.width}
      height={image.height}
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      className={cn('h-auto w-full object-cover', className)}
      {...props}
    />
  );
}
