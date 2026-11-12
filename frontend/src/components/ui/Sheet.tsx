import * as RadixDialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { AnimatePresence, m, useDragControls, type PanInfo } from 'motion/react';
import { useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useReturnFocus } from '@/hooks/useReturnFocus';
import { cn } from '@/lib/cn';
import { duration, ease, exitDuration } from '@/lib/motion/tokens';

interface SheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Accessible title (may be visually hidden by the caller's header). */
  title: ReactNode;
  description?: ReactNode;
  /** Content of the top bar next to the close button. */
  header?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** Share of the panel's width that a swipe to the right must travel to close it. */
const CLOSE_DISTANCE = 0.3;

/**
 * A panel sliding in from the right edge, where its triggers (menu, cart) sit, and leaving
 * the same way. It follows a finger or the mouse 1:1 while held (no easing during the drag),
 * rubber-bands when pulled the wrong way, and closes when swiped far or fast enough.
 * Focus trapping, Escape and the overlay click come from Radix Dialog. Because the cart sheet
 * is often opened from code (after "Add to cart") and not by a trigger, the sheet itself
 * remembers what had focus when it opened and returns focus there when it closes.
 */
export function Sheet({ open, onOpenChange, title, description, header, children, className }: SheetProps) {
  const { t } = useTranslation();
  const drag = useDragControls();
  const panel = useRef<HTMLDivElement>(null);
  const returnFocus = useReturnFocus();

  const onDragEnd = (_event: unknown, info: PanInfo) => {
    const width = panel.current?.clientWidth ?? 400;
    if (info.offset.x > width * CLOSE_DISTANCE || info.velocity.x > 600) onOpenChange(false);
  };

  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <AnimatePresence>
        {open && (
          <RadixDialog.Portal forceMount>
            <RadixDialog.Overlay asChild forceMount>
              <m.div
                className="fixed inset-0 z-50 bg-ink/30"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1, transition: { duration: duration.base, ease: ease.out } }}
                exit={{ opacity: 0, transition: { duration: exitDuration(duration.base), ease: ease.out } }}
              />
            </RadixDialog.Overlay>
            <RadixDialog.Content asChild forceMount {...returnFocus}>
              <m.div
                ref={panel}
                initial={{ x: '100%' }}
                animate={{ x: 0, transition: { duration: 0.3, ease: ease.out } }}
                exit={{ x: '100%', transition: { duration: exitDuration(0.3), ease: ease.out } }}
                drag="x"
                dragListener={false}
                dragControls={drag}
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={{ left: 0.08, right: 1 }}
                dragMomentum={false}
                onDragEnd={onDragEnd}
                className={cn(
                  'fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col bg-surface shadow-lift focus:outline-none',
                  className,
                )}
              >
                <div
                  className="flex touch-pan-y items-center justify-between gap-4 border-b border-line px-6 py-4"
                  onPointerDown={(event) => drag.start(event)}
                >
                  <div className="min-w-0">
                    {header}
                    <RadixDialog.Title className={header ? 'sr-only' : 'font-display text-xl'}>
                      {title}
                    </RadixDialog.Title>
                    {description ? (
                      <RadixDialog.Description className="mt-0.5 text-sm text-muted">
                        {description}
                      </RadixDialog.Description>
                    ) : (
                      <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
                    )}
                  </div>
                  <RadixDialog.Close
                    className="-mr-2 inline-flex size-11 shrink-0 press items-center justify-center rounded-full hover:bg-brand-50 active:bg-brand-100"
                    aria-label={t('common.close')}
                  >
                    <X className="size-5" strokeWidth={1.5} />
                  </RadixDialog.Close>
                </div>
                {children}
              </m.div>
            </RadixDialog.Content>
          </RadixDialog.Portal>
        )}
      </AnimatePresence>
    </RadixDialog.Root>
  );
}
