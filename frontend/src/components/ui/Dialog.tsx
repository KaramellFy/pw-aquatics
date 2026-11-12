import * as RadixDialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { AnimatePresence, m } from 'motion/react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useTriggerOrigin } from '@/components/motion/useTriggerOrigin';
import { useReturnFocus } from '@/hooks/useReturnFocus';
import { cn } from '@/lib/cn';
import { duration, ease, exitDuration, POPOVER_START_SCALE } from '@/lib/motion/tokens';

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Modal dialog: focus is trapped, Escape closes, and focus returns to the trigger.
 * On larger screens it grows from the button that opened it (from 95 %) and shrinks back
 * into it; on phones it slides up as a bottom sheet. Exits are 20 % shorter than entrances.
 */
export function Dialog({ open, onOpenChange, title, description, children, className }: DialogProps) {
  const { t } = useTranslation();
  const wide = useMediaQuery('(min-width: 40rem)');
  const panel = useTriggerOrigin<HTMLDivElement>(open);
  const returnFocus = useReturnFocus();

  const enter = { duration: duration.base, ease: ease.out };
  const leave = { duration: exitDuration(duration.base), ease: ease.out };
  const hidden = wide ? { opacity: 0, scale: POPOVER_START_SCALE } : { opacity: 0, y: '100%' };

  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <AnimatePresence>
        {open && (
          <RadixDialog.Portal forceMount>
            <RadixDialog.Overlay asChild forceMount>
              <m.div
                className="fixed inset-0 z-50 bg-ink/30"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1, transition: enter }}
                exit={{ opacity: 0, transition: leave }}
              />
            </RadixDialog.Overlay>
            <RadixDialog.Content asChild forceMount {...returnFocus}>
              <m.div
                ref={panel}
                initial={hidden}
                animate={{ opacity: 1, scale: 1, y: 0, transition: enter }}
                exit={{ ...hidden, transition: leave }}
                className={cn(
                  'fixed inset-x-0 bottom-0 z-50 max-h-[92dvh] overflow-y-auto rounded-t-2xl bg-surface p-6 shadow-lift focus:outline-none',
                  'sm:inset-x-auto sm:top-1/2 sm:bottom-auto sm:left-1/2 sm:w-full sm:max-w-xl sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-2xl sm:p-8',
                  className,
                )}
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <RadixDialog.Title className="font-display text-2xl">{title}</RadixDialog.Title>
                    {description ? (
                      <RadixDialog.Description className="mt-2 text-ink-soft">
                        {description}
                      </RadixDialog.Description>
                    ) : (
                      <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
                    )}
                  </div>
                  <RadixDialog.Close
                    className="-mt-1 -mr-2 inline-flex size-11 shrink-0 press items-center justify-center rounded-full hover:bg-brand-50 active:bg-brand-100"
                    aria-label={t('common.close')}
                  >
                    <X className="size-5" strokeWidth={1.5} />
                  </RadixDialog.Close>
                </div>
                <div className="mt-6">{children}</div>
              </m.div>
            </RadixDialog.Content>
          </RadixDialog.Portal>
        )}
      </AnimatePresence>
    </RadixDialog.Root>
  );
}
