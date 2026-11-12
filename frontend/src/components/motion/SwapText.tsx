import { AnimatePresence, m } from 'motion/react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { duration, ease, exitDuration } from '@/lib/motion/tokens';

/**
 * Replaces a short text or number with the next one: the old value leaves while the new
 * one arrives in the same spot, so for a moment both overlap instead of the text jumping.
 * `direction` 1 slides upwards (a count going up), -1 downwards, 0 just crossfades.
 */
export function SwapText({
  value,
  children,
  direction = 0,
  className,
}: {
  /** Identity of the content; a new value triggers the swap. */
  value: string | number;
  children?: ReactNode;
  direction?: 1 | 0 | -1;
  className?: string;
}) {
  const offset = direction * 0.6;
  return (
    <span className={cn('inline-grid overflow-hidden align-bottom', className)}>
      <AnimatePresence mode="popLayout" initial={false} custom={offset}>
        <m.span
          key={value}
          className="col-start-1 row-start-1"
          initial={{ opacity: 0, y: `${offset * 100}%`, filter: 'blur(2px)' }}
          animate={{
            opacity: 1,
            y: '0%',
            filter: 'blur(0px)',
            transition: { duration: duration.menu, ease: ease.out },
          }}
          exit={{
            opacity: 0,
            y: `${-offset * 100}%`,
            filter: 'blur(2px)',
            transition: { duration: exitDuration(duration.menu), ease: ease.out },
          }}
        >
          {children ?? value}
        </m.span>
      </AnimatePresence>
    </span>
  );
}
