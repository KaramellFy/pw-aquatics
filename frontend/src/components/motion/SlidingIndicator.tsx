import { m } from 'motion/react';
import { useRef } from 'react';
import { cn } from '@/lib/cn';
import { ease, prefersReducedMotion } from '@/lib/motion/tokens';

/** How long the indicator takes to travel to a new option. */
const TRAVEL = 0.32;

/**
 * A marker that moves between options: the active menu link, the selected pack size, the
 * current language. Every instance with the same `layoutId` is one object, so when the
 * selection changes it travels to its new place (easing in and out, no bounce) instead of
 * blinking off and on. A small knob also stretches by 12 % along the way, like something
 * being pulled, and settles back when it arrives.
 *
 * Render it inside the selected option only; the option needs `position: relative`. While it
 * travels it passes over the other options, so their text (and any heading in between) needs
 * `relative z-10` to stay on top.
 */
export function SlidingIndicator({
  layoutId,
  className,
  markClassName,
  stretch = 'none',
}: {
  layoutId: string;
  /** Position, size and corner radius of the marker (it is absolutely positioned). */
  className?: string;
  /** Its look: background, border. */
  markClassName?: string;
  /** Axis along which a small knob stretches while moving; 'none' for large frames. */
  stretch?: 'x' | 'y' | 'none';
}) {
  const mark = useRef<HTMLSpanElement>(null);
  return (
    <m.span
      layoutId={layoutId}
      aria-hidden
      className={cn('pointer-events-none absolute', className)}
      transition={{ layout: { duration: TRAVEL, ease: ease.inOut } }}
      onLayoutAnimationStart={() => {
        const element = mark.current;
        // `animate` is missing in some test environments (jsdom).
        if (stretch === 'none' || !element || typeof element.animate !== 'function') return;
        if (prefersReducedMotion()) return;
        // The browser's own animation API is enough here and keeps Motion's engine out of
        // the first download.
        const axis = stretch === 'x' ? 'scaleX' : 'scaleY';
        element.animate(
          [{ transform: `${axis}(1)` }, { transform: `${axis}(1.12)` }, { transform: `${axis}(1)` }],
          { duration: TRAVEL * 1000, easing: 'ease-in-out' },
        );
      }}
    >
      <span ref={mark} className={cn('block size-full rounded-[inherit]', markClassName)} />
    </m.span>
  );
}
