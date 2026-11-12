import { useId, useState, type KeyboardEvent } from 'react';
import { cn } from '@/lib/cn';
import { columnPath, labelEvery, niceTicks } from './scale';
import { useElementWidth } from './useElementWidth';

export interface ColumnSeries {
  key: string;
  label: string;
  /** Tailwind fill class for the column, e.g. `fill-brand-800`. */
  fillClassName: string;
  /** Matching background class for legend and tooltip keys, e.g. `bg-brand-800`. */
  swatchClassName: string;
}

export interface ColumnPoint {
  key: string;
  /** Short label under the column ("12 Nov"). */
  axisLabel: string;
  /** Full label in the tooltip ("Week of 10 November 2026"). */
  title: string;
  /** Height of the whole column: the total the API sent for this point. */
  total: number;
  /** Value of each series, stacked from the first series upwards. */
  values: Record<string, number>;
}

interface ColumnChartProps {
  /** Accessible name of the chart. */
  label: string;
  series: ColumnSeries[];
  points: ColumnPoint[];
  /** Formats a value for the tooltip. */
  formatValue: (value: number) => string;
  /** Formats an axis tick (usually compact). */
  formatTick: (value: number) => string;
  /** Label of the total in the tooltip, when there is more than one series. */
  totalLabel?: string;
  integer?: boolean;
  emptyTop?: number;
  height?: number;
}

const MARGIN = { top: 8, right: 4, bottom: 28 };
const GAP = 2;
const MAX_BAR = 24;

/**
 * Columns over time or categories, stacked when there are several series (docs: dataviz
 * marks). Hover a column, or focus the chart and use the arrow keys, to read its values.
 * Every value is also available in the card's table view.
 */
export function ColumnChart({
  label,
  series,
  points,
  formatValue,
  formatTick,
  totalLabel,
  integer = false,
  emptyTop = 1,
  height = 240,
}: ColumnChartProps) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);
  const tooltipId = useId();

  const max = Math.max(0, ...points.map((point) => point.total));
  const ticks = niceTicks(max, { integer, emptyTop });
  const top = ticks[ticks.length - 1] ?? 1;
  const tickLabels = ticks.map(formatTick);
  const left = Math.max(...tickLabels.map((text) => text.length)) * 7 + 12;
  const plotWidth = Math.max(1, width - left - MARGIN.right);
  const plotHeight = height - MARGIN.top - MARGIN.bottom;
  const baseline = MARGIN.top + plotHeight;
  const slot = plotWidth / Math.max(1, points.length);
  const barWidth = Math.max(2, Math.min(MAX_BAR, slot * 0.66));
  const every = labelEvery(points.length, plotWidth);
  const y = (value: number) => (value / top) * plotHeight;

  const move = (event: KeyboardEvent<HTMLDivElement>) => {
    if (points.length === 0) return;
    const current = active ?? points.length - 1;
    const next =
      event.key === 'ArrowLeft'
        ? Math.max(0, current - 1)
        : event.key === 'ArrowRight'
          ? Math.min(points.length - 1, current + 1)
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? points.length - 1
              : null;
    if (next === null) {
      if (event.key === 'Escape') setActive(null);
      return;
    }
    event.preventDefault();
    setActive(next);
  };

  const shown = active === null ? null : points[active];
  const tooltipLeft =
    active === null ? 0 : Math.min(Math.max(left + slot * (active + 0.5), 88), Math.max(88, width - 88));

  return (
    <div ref={ref} className="relative">
      <div
        role="group"
        aria-label={label}
        aria-describedby={shown ? tooltipId : undefined}
        tabIndex={0}
        onKeyDown={move}
        onFocus={() => setActive((current) => current ?? points.length - 1)}
        onBlur={() => setActive(null)}
        className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand-700"
      >
        <svg
          width={width}
          height={height}
          viewBox={`0 0 ${width} ${height}`}
          aria-hidden
          className="block overflow-visible"
          onPointerLeave={() => setActive(null)}
        >
          {ticks.map((tick, index) => (
            <g key={tick}>
              <line
                x1={left}
                x2={left + plotWidth}
                y1={baseline - y(tick)}
                y2={baseline - y(tick)}
                className={index === 0 ? 'stroke-line-strong' : 'stroke-line'}
                strokeWidth={1}
                shapeRendering="crispEdges"
              />
              <text
                x={left - 8}
                y={baseline - y(tick)}
                dy="0.32em"
                textAnchor="end"
                className="fill-muted text-[0.6875rem] tabular-nums"
              >
                {tickLabels[index]}
              </text>
            </g>
          ))}

          {points.map((point, index) => {
            const x = left + slot * index + (slot - barWidth) / 2;
            let stacked = 0;
            const visible = series.filter((item) => (point.values[item.key] ?? 0) > 0);
            return (
              <g key={point.key}>
                {active === index && (
                  <rect
                    x={left + slot * index}
                    y={MARGIN.top}
                    width={slot}
                    height={plotHeight}
                    className="fill-ink/5"
                  />
                )}
                {visible.map((item, position) => {
                  const value = point.values[item.key] ?? 0;
                  const bottom = baseline - y(stacked) - (position > 0 ? GAP : 0);
                  stacked += value;
                  const segmentTop = baseline - y(stacked);
                  const segmentHeight = Math.max(1, bottom - segmentTop);
                  const isTop = position === visible.length - 1;
                  return isTop ? (
                    <path
                      key={item.key}
                      d={columnPath(x, bottom - segmentHeight, barWidth, segmentHeight)}
                      className={item.fillClassName}
                    />
                  ) : (
                    <rect
                      key={item.key}
                      x={x}
                      y={bottom - segmentHeight}
                      width={barWidth}
                      height={segmentHeight}
                      className={item.fillClassName}
                    />
                  );
                })}
                {(points.length - 1 - index) % every === 0 && (
                  <text
                    x={left + slot * (index + 0.5)}
                    y={baseline + 18}
                    textAnchor="middle"
                    className="fill-muted text-[0.6875rem]"
                  >
                    {point.axisLabel}
                  </text>
                )}
                {/* Hit target: the whole slot, taller and wider than the column. */}
                <rect
                  x={left + slot * index}
                  y={MARGIN.top}
                  width={slot}
                  height={plotHeight + MARGIN.bottom}
                  fill="transparent"
                  onPointerEnter={() => setActive(index)}
                />
              </g>
            );
          })}
        </svg>
      </div>

      {shown && (
        <div
          id={tooltipId}
          role="status"
          className={cn(
            'pointer-events-none absolute top-0 z-10 w-max max-w-[15rem] -translate-x-1/2 -translate-y-2',
            'rounded-xl border border-line bg-surface px-3 py-2 text-sm shadow-soft',
          )}
          style={{ left: tooltipLeft }}
        >
          <p className="text-xs text-muted">{shown.title}</p>
          {series.length > 1 && (
            <p className="mt-1 flex items-baseline justify-between gap-4">
              <span className="font-semibold text-ink tabular-nums">{formatValue(shown.total)}</span>
              <span className="text-xs text-ink-soft">{totalLabel}</span>
            </p>
          )}
          <ul className="mt-1 space-y-0.5">
            {series.map((item) => (
              <li key={item.key} className="flex items-center gap-2">
                <span aria-hidden className={cn('h-0.5 w-3 shrink-0 rounded-full', item.swatchClassName)} />
                <span
                  className={cn(
                    'tabular-nums',
                    series.length > 1 ? 'text-ink-soft' : 'font-semibold text-ink',
                  )}
                >
                  {formatValue(shown.values[item.key] ?? 0)}
                </span>
                <span className="text-xs text-ink-soft">{item.label}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
