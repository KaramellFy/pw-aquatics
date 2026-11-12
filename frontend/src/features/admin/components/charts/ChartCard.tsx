import { useState, type ReactNode } from 'react';
import { BarChart3, Table2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';

/**
 * A card holding one chart, with a switch to the same numbers as a table (so no value is
 * only reachable by hovering) and an optional control row (e.g. the metric switch).
 */
export function ChartCard({
  id,
  title,
  description,
  controls,
  legend,
  table,
  dimmed = false,
  className,
  children,
}: {
  id: string;
  title: string;
  description?: string;
  controls?: ReactNode;
  legend?: ReactNode;
  /** The same data as a table; omit when the chart already prints every value. */
  table?: ReactNode;
  /** While newer data loads, the old chart stays in place, slightly faded. */
  dimmed?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const [asTable, setAsTable] = useState(false);
  return (
    <section
      aria-labelledby={`${id}-title`}
      className={cn('min-w-0 rounded-card border border-line bg-surface p-5 sm:p-6', className)}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h2 id={`${id}-title`} className="text-title">
            {title}
          </h2>
          {description && <p className="mt-1 text-sm text-ink-soft">{description}</p>}
        </div>
        {table && (
          <button
            type="button"
            aria-pressed={asTable}
            onClick={() => setAsTable((value) => !value)}
            className="-mt-2 -mr-2 inline-flex min-h-11 shrink-0 items-center gap-2 rounded-full px-3 text-sm text-ink-soft hover:bg-ink/5"
          >
            {asTable ? (
              <BarChart3 aria-hidden className="size-4" />
            ) : (
              <Table2 aria-hidden className="size-4" />
            )}
            <span className="sr-only sm:not-sr-only">
              {asTable ? t('admin.dashboard.showChart') : t('admin.dashboard.showTable')}
            </span>
          </button>
        )}
      </div>
      {controls && <div className="mt-4">{controls}</div>}
      <div className={cn('mt-5 transition-opacity', dimmed && 'opacity-60')} aria-busy={dimmed || undefined}>
        {asTable ? table : children}
      </div>
      {legend && !asTable && <div className="mt-4">{legend}</div>}
    </section>
  );
}

/** Legend of a chart with several series: a swatch shaped like the mark, then the name. */
export function Legend({ items }: { items: { key: string; label: string; swatchClassName: string }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-soft">
      {items.map((item) => (
        <li key={item.key} className="flex items-center gap-2">
          <span aria-hidden className={cn('size-3 rounded-[3px]', item.swatchClassName)} />
          {item.label}
        </li>
      ))}
    </ul>
  );
}

/** A small table used as the "show as table" view of a chart. */
export function ChartTable({
  caption,
  columns,
  rows,
}: {
  caption: string;
  columns: { key: string; label: string; numeric?: boolean }[];
  rows: { key: string; cells: Record<string, string> }[];
}) {
  return (
    <div className="relative max-h-[24rem] overflow-auto rounded-xl border border-line">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="sticky top-0 border-b border-line bg-surface text-xs tracking-wide text-muted uppercase">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cn('px-3 py-2 font-medium', column.numeric && 'text-right')}
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((row) => (
            <tr key={row.key}>
              {columns.map((column, index) =>
                index === 0 ? (
                  <th key={column.key} scope="row" className="px-3 py-2 font-normal text-ink">
                    {row.cells[column.key]}
                  </th>
                ) : (
                  <td
                    key={column.key}
                    className={cn('px-3 py-2 text-ink', column.numeric && 'text-right tabular-nums')}
                  >
                    {row.cells[column.key]}
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
