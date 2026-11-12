import { AnimatePresence, m } from 'motion/react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { duration, ease, exitDuration } from '@/lib/motion/tokens';

/**
 * Swaps one icon for another with a short crossfade, a slight scale and a blur, so the
 * change reads as one object transforming rather than two objects jumping. Give each state
 * its own `stateKey` (e.g. "bag" → "check").
 */
export function CrossfadeIcon({
  stateKey,
  children,
  className,
}: {
  stateKey: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span className={cn('relative inline-grid size-4 shrink-0 place-items-center', className)} aria-hidden>
      <AnimatePresence mode="popLayout" initial={false}>
        <m.span
          key={stateKey}
          className="col-start-1 row-start-1 inline-grid place-items-center"
          initial={{ opacity: 0, scale: 0.5, filter: 'blur(4px)' }}
          animate={{
            opacity: 1,
            scale: 1,
            filter: 'blur(0px)',
            transition: { duration: duration.menu, ease: ease.out },
          }}
          exit={{
            opacity: 0,
            scale: 0.5,
            filter: 'blur(4px)',
            transition: { duration: exitDuration(duration.menu), ease: ease.out },
          }}
        >
          {children}
        </m.span>
      </AnimatePresence>
    </span>
  );
}
