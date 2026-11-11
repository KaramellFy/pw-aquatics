import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface OptionCardProps {
  /** Radio group name. */
  name: string;
  value: string;
  checked: boolean;
  onChange: () => void;
  children: ReactNode;
  className?: string;
}

/** A radio button presented as a selectable card; the whole card is the click target. */
export function OptionCard({ name, value, checked, onChange, children, className }: OptionCardProps) {
  return (
    <label
      className={cn(
        'flex cursor-pointer gap-4 rounded-2xl border p-5 transition-colors',
        'has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-brand-700',
        checked ? 'border-brand-700 bg-brand-50' : 'border-line-strong hover:border-ink-soft',
        className,
      )}
    >
      <input
        type="radio"
        name={name}
        value={value}
        checked={checked}
        onChange={onChange}
        className="mt-1 size-5 shrink-0 cursor-pointer accent-brand-700 outline-none"
      />
      <span className="min-w-0 flex-1">{children}</span>
    </label>
  );
}
