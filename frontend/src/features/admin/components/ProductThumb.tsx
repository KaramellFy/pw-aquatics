import { ResponsiveImage, type ImageSource } from '@/components/ui/ResponsiveImage';

/** Small square product photo for admin lists; an empty tile when there is none yet. */
export function ProductThumb({ image }: { image: ImageSource | null | undefined }) {
  return (
    <div className="size-12 shrink-0 overflow-hidden rounded-xl bg-mist">
      {image && <ResponsiveImage image={image} alt="" sizes="48px" className="size-full" />}
    </div>
  );
}
