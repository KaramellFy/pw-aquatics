import { cn } from '@/lib/cn';

export interface RankedRow {
  key: string;
  label: string;
  /** Size of the bar (same unit for every row); negative values are drawn by their size. */
  value: number;
  /** The formatted value printed at the end of the row. */
  display: string;
  /** Optional second figure in quieter text, e.g. "30 animals". */
  secondary?: string;
}

/**
 * Ranked horizontal bars ("meter list"): a name, a bar and a figure per row, largest first,
 * one hue. Every value is printed, so nothing depends on hovering.
 */
export function RankedBars({
  label,
  rows,
  emptyText,
  barClassName = 'bg-brand-600',
}: {
  label: string;
  rows: RankedRow[];
  emptyText: string;
  barClassName?: string;
}) {
  if (rows.length === 0) {
    return <p className="py-6 text-sm text-muted">{emptyText}</p>;
  }
  const max = Math.max(...rows.map((row) => Math.abs(row.value)), 1);
  return (
    <ol aria-label={label} className="space-y-3">
      {rows.map((row) => (
        <li key={row.key} className="grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-4 gap-y-1.5">
          <span className="truncate text-[0.9375rem] text-ink" title={row.label}>
            {row.label}
          </span>
          <span className="text-right text-[0.9375rem] font-medium whitespace-nowrap text-ink tabular-nums">
            {row.display}
            {row.secondary && <span className="ml-2 text-xs font-normal text-muted">{row.secondary}</span>}
          </span>
          <span aria-hidden className="col-span-2 block h-2.5">
            <span
              className={cn('block h-full rounded-r-[4px]', barClassName)}
              style={{ width: `${Math.max((Math.abs(row.value) / max) * 100, row.value === 0 ? 0 : 1)}%` }}
            />
          </span>
        </li>
      ))}
    </ol>
  );
}
