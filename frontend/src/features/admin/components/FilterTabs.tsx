import { SlidingIndicator } from '@/components/motion/SlidingIndicator';
import { cn } from '@/lib/cn';
import { formatCount } from '@/lib/format/number';
import { useIntlLocale } from '@/lib/i18n';

export interface FilterOption<T extends string> {
  value: T;
  label: string;
  /** Shown next to the label, e.g. unread messages. */
  count?: number;
}

/** A row of mutually exclusive quick filters (e.g. "To ship"), the chosen one marked. */
export function FilterTabs<T extends string>({
  id,
  label,
  options,
  value,
  onChange,
  className,
}: {
  /** Unique per page; keeps the moving marker of different rows apart. */
  id: string;
  label: string;
  options: FilterOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  const locale = useIntlLocale();
  return (
    <div
      role="group"
      aria-label={label}
      className={cn('-mx-1 flex gap-1 overflow-x-auto px-1 pb-1 sm:flex-wrap sm:overflow-visible', className)}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              'relative inline-flex min-h-11 shrink-0 press items-center gap-2 rounded-full px-4 text-[0.9375rem] whitespace-nowrap transition-colors',
              selected ? 'font-medium text-white' : 'text-ink-soft hover:bg-surface',
            )}
          >
            {selected && (
              <SlidingIndicator
                layoutId={`filter-${id}`}
                className="inset-0 rounded-full"
                markClassName="bg-ink"
              />
            )}
            <span className="relative z-10">{option.label}</span>
            {option.count !== undefined && option.count > 0 && (
              <span
                className={cn(
                  'relative z-10 rounded-full px-2 text-xs tabular-nums',
                  selected ? 'bg-white/20 text-white' : 'bg-brand-50 text-brand-800',
                )}
              >
                {formatCount(option.count, locale)}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
