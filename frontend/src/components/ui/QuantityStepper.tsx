import { Minus, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';

interface QuantityStepperProps {
  value: number;
  onChange: (value: number) => void;
  /** Largest allowed value (from the server); the minimum is 1. */
  max: number;
  /** Accessible name, e.g. "Quantity of Blue Dream". */
  label: string;
  disabled?: boolean;
  className?: string;
}

const button =
  'inline-flex size-11 items-center justify-center rounded-full text-ink transition-colors hover:bg-brand-50 active:bg-brand-100 disabled:pointer-events-none disabled:opacity-35';

/** Minus / value / plus control with 44px buttons. */
export function QuantityStepper({
  value,
  onChange,
  max,
  label,
  disabled = false,
  className,
}: QuantityStepperProps) {
  const { t } = useTranslation();
  return (
    <div
      role="group"
      aria-label={label}
      className={cn('inline-flex items-center rounded-full border border-line-strong bg-surface', className)}
    >
      <button
        type="button"
        className={button}
        aria-label={t('cart.quantity.decrease')}
        disabled={disabled || value <= 1}
        onClick={() => onChange(value - 1)}
      >
        <Minus aria-hidden className="size-4" />
      </button>
      <output aria-live="polite" className="min-w-8 text-center text-[0.9375rem] font-medium tabular-nums">
        {value}
      </output>
      <button
        type="button"
        className={button}
        aria-label={t('cart.quantity.increase')}
        disabled={disabled || value >= max}
        onClick={() => onChange(value + 1)}
      >
        <Plus aria-hidden className="size-4" />
      </button>
    </div>
  );
}
