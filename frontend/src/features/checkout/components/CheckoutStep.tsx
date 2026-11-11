import { Check } from 'lucide-react';
import type { ReactNode, Ref } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';

export type StepState = 'current' | 'done' | 'upcoming';

interface CheckoutStepProps {
  /** 1-based position. */
  number: number;
  total: number;
  title: string;
  state: StepState;
  /** Shown instead of the content once the step is done. */
  summary?: ReactNode;
  onEdit?: () => void;
  headingRef?: Ref<HTMLHeadingElement>;
  children: ReactNode;
}

/** One numbered section of the checkout: open while current, summarised once done. */
export function CheckoutStep({
  number,
  total,
  title,
  state,
  summary,
  onEdit,
  headingRef,
  children,
}: CheckoutStepProps) {
  const { t } = useTranslation();
  return (
    <section
      aria-current={state === 'current' ? 'step' : undefined}
      className={cn(
        'rounded-card border p-6 sm:p-8',
        state === 'current' ? 'border-brand-200 bg-surface' : 'border-line bg-surface',
      )}
    >
      <div className="flex items-center gap-4">
        <span
          aria-hidden
          className={cn(
            'flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-medium',
            state === 'current' && 'bg-ink text-white',
            state === 'done' && 'bg-brand-100 text-brand-800',
            state === 'upcoming' && 'border border-line-strong text-muted',
          )}
        >
          {state === 'done' ? <Check className="size-4" /> : number}
        </span>
        <h2
          ref={headingRef}
          tabIndex={-1}
          className={cn('flex-1 text-title outline-none', state === 'upcoming' && 'text-muted')}
        >
          <span className="sr-only">{t('checkout.stepOf', { step: number, total })}: </span>
          {title}
        </h2>
        {state === 'done' && onEdit && (
          <Button variant="ghost" aria-label={t('checkout.changeLabel', { step: title })} onClick={onEdit}>
            {t('checkout.change')}
          </Button>
        )}
      </div>
      {state === 'current' && <div className="mt-6">{children}</div>}
      {state === 'done' && summary && <div className="mt-4 pl-13 text-ink-soft">{summary}</div>}
    </section>
  );
}
