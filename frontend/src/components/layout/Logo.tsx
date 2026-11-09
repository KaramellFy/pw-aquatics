import { cn } from '@/lib/cn';

/**
 * Temporary wordmark until the final logo is delivered:
 * a small water-drop mark with a curled "shrimp" line, plus the brand name.
 */
export function Logo({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-3', className)}>
      <svg viewBox="0 0 32 32" aria-hidden className="size-8 shrink-0">
        <circle cx="16" cy="16" r="15" className={inverted ? 'fill-paper' : 'fill-ink'} />
        <path
          d="M10 19.5c1.5 3 5.5 4.2 8.6 2.4 3.4-2 3.9-6.6 1.2-9.2-2.1-2-5.4-1.9-7.1.3-1.3 1.7-.9 4 .9 5"
          fill="none"
          strokeWidth="1.6"
          strokeLinecap="round"
          className={inverted ? 'stroke-ink' : 'stroke-paper'}
        />
        <circle cx="20.6" cy="12.6" r="1.1" className={inverted ? 'fill-ember-500' : 'fill-ember-500'} />
      </svg>
      <span className="flex flex-col leading-none">
        <span className="font-serif text-[1.35rem] tracking-tight">Wakup</span>
        <span className="mt-1 text-[0.62rem] font-medium tracking-[0.32em] uppercase opacity-70">
          Wirbellose
        </span>
      </span>
    </span>
  );
}
