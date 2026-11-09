import { cn } from '@/lib/cn';
import type { Colour } from '../api/catalog';

/** Small colour swatches; names are available to screen readers and on hover. */
export function ColourDots({ colours, className }: { colours: Colour[]; className?: string }) {
  if (colours.length === 0) return null;
  return (
    <ul className={cn('flex items-center gap-1.5', className)}>
      {colours.map((colour) => (
        <li key={colour.slug} title={colour.name}>
          <span
            className="block size-3.5 rounded-full ring-1 ring-ink/15 ring-offset-1 ring-offset-surface"
            style={{ backgroundColor: colour.hexCode ?? undefined }}
          />
          <span className="sr-only">{colour.name}</span>
        </li>
      ))}
    </ul>
  );
}
